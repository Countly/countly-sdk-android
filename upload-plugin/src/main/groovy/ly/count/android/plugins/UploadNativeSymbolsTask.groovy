package ly.count.android.plugins

import groovy.io.FileType
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Runs dump_syms over every .so below nativeLibs, lays the results out the way minidump_stackwalk
 * searches a symbol store (symbols/<lib>/<debug id>/<lib>.sym), tars the store and uploads it.
 *
 * Everything the action needs arrives through Gradle properties, so the task works under the
 * configuration cache and can be pointed at any directory: the libraries AGP merges for a variant,
 * or one the integrator names.
 */
@DisableCachingByDefault(because = 'Uploading is the point of the task; there is nothing to reuse from a cache')
abstract class UploadNativeSymbolsTask extends DefaultTask {
  /** Searched recursively for .so files. */
  @InputDirectory
  @PathSensitive(PathSensitivity.RELATIVE)
  abstract DirectoryProperty getNativeLibs()

  @Input
  abstract Property<String> getServer()

  @Input
  abstract Property<String> getAppKey()

  /** Stored with the upload; the dashboard offers these symbols to crashes of this app version. */
  @Input
  abstract Property<String> getVersionName()

  @Input
  abstract Property<String> getNote()

  /** The directory holding the dump_syms executable. */
  @Input
  abstract Property<String> getDumpSymsPath()

  /** Where the symbol store and the archive are assembled. */
  @OutputDirectory
  abstract DirectoryProperty getWorkDir()

  UploadNativeSymbolsTask() {
    group = 'countly'
    // An upload is a side effect. Never let Gradle decide it already happened.
    outputs.upToDateWhen { false }
  }

  @TaskAction
  void dumpAndUpload() {
    requireSetting(server, 'server')
    requireSetting(appKey, 'app_key')

    File libsDir = nativeLibs.get().asFile
    File work = workDir.get().asFile
    work.deleteDir()
    work.mkdirs()

    List<File> objects = []
    libsDir.traverse(type: FileType.FILES, nameFilter: ~/.*\.so$/) { objects << it }
    if (objects.isEmpty()) {
      throw new GradleException("${name}: no .so files under ${libsDir}, nothing to upload.")
    }
    objects.sort()

    String dumpSyms = "${dumpSymsPath.get()}/dump_syms"
    List<String> modules = []
    for (File object : objects) {
      BreakpadSymbols.Module module = dumpSymbols(dumpSyms, object, work)
      // The debug id is the only link between a crash and this symbol file. Logging it lets a crash
      // that stays unsymbolicated be checked against the library inside the APK that actually shipped.
      logger.lifecycle("${name}: ${module.describe()}  <- ${libsDir.toPath().relativize(object.toPath())}")
      modules << module.describe()
    }
    new File(work, 'modules.txt').text = modules.join('\n') + '\n'

    File archive = new File(work, 'symbols.tar.gz')
    new groovy.ant.AntBuilder().tar(destfile: archive.path, basedir: work.path, includes: 'symbols/**', compression: 'gzip')

    upload(archive, dumpSymsVersion(dumpSyms))
  }

  private void requireSetting(Property<String> property, String settingName) {
    if (!property.isPresent() || property.get().trim().isEmpty()) {
      throw new GradleException("${name}: countly { ${settingName} } is not set. Both server and app_key are needed to upload symbols.")
    }
  }

  /** Dumps one object into the store and returns its MODULE record. */
  private BreakpadSymbols.Module dumpSymbols(String dumpSyms, File object, File work) {
    File raw = File.createTempFile('dump_syms-', '.sym', work)
    try {
      Process process = new ProcessBuilder(dumpSyms, object.absolutePath)
          .redirectOutput(raw)
          .redirectError(ProcessBuilder.Redirect.INHERIT)
          .start()
      int exitCode = process.waitFor()
      if (exitCode != 0) {
        throw new GradleException("${name}: ${dumpSyms} failed with exit code ${exitCode} for ${object}")
      }

      BreakpadSymbols.Module module = null
      raw.withReader { module = BreakpadSymbols.parseModuleLine(it.readLine()) }
      File symbolFile = new File(work, module.storePath())
      symbolFile.parentFile.mkdirs()
      Files.move(raw.toPath(), symbolFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
      return module
    } catch (IllegalArgumentException e) {
      throw new GradleException("${name}: ${object}: ${e.message}", e)
    } catch (IOException e) {
      throw new GradleException("${name}: could not run ${dumpSyms} (is dumpSymsPath right?): ${e.message}", e)
    } finally {
      raw.delete()
    }
  }

  /**
   * dump_syms built from Countly's breakpad fork reports "0.1+cly". Anything else, including the
   * mozilla/dump_syms builds that work on macOS and Windows, is recorded as a plain breakpad build.
   */
  private static String dumpSymsVersion(String dumpSyms) {
    String version = ''
    try {
      Process process = new ProcessBuilder(dumpSyms, '--version').redirectErrorStream(true).start()
      version = process.inputStream.text.trim()
      process.waitFor()
    } catch (IOException ignored) {
      // Reported as a plain breakpad build below.
    }
    (version ==~ /\d+\.\d+\+cly/) ? version : '0.1+bpd'
  }

  private void upload(File archive, String toolVersion) {
    String base = server.get()
    String url = (base.endsWith('/') ? base : base + '/') + 'i/crash_symbols/upload_symbol'
    RequestBody body = new MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart('symbols', archive.name, RequestBody.create(MediaType.parse('application/gzip'), archive))
        .addFormDataPart('platform', 'android_native')
        .addFormDataPart('app_key', appKey.get())
        .addFormDataPart('build', versionName.get())
        .addFormDataPart('note', note.get())
        .addFormDataPart('sym_tool_ver', toolVersion)
        .build()
    Request request = new Request.Builder().url(url).post(body).build()

    Response response = new OkHttpClient().newCall(request).execute()
    try {
      String responseBody = response.body()?.string()
      if (response.code() != 200) {
        throw new GradleException("${name}: uploading ${archive.name} to ${url} failed with HTTP ${response.code()}: ${responseBody}")
      }
      logger.lifecycle("${name}: uploaded ${archive.length()} bytes of symbols for version ${versionName.get()} to ${base}")
    } finally {
      response.close()
    }
  }
}
