package ly.count.android.plugins

import okhttp3.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.StopActionException
import org.gradle.api.tasks.TaskProvider

class UploadSymbolsPluginExtension {
  String app_key = ""
  String server = ""
  String mappingFile = "outputs/mapping/release/mapping.txt"
  String dumpSymsPath = "/usr/bin"
  /**
   * Directory of .so files to dump, relative to the build directory, where BUILD_TYPE stands for the
   * variant's build type. Unset means the libraries AGP merges for the variant, which is what the APK
   * ships: the module's own, still unstripped, plus the ones pulled out of AARs.
   */
  String nativeObjectFilesDir = null
  String noteJava = "sdk-plugin automatic upload of mapping.txt"
  String noteNative = "sdk-plugin automatic upload of breakpad symbols"
}

class UploadSymbolsPlugin implements Plugin<Project> {
  void apply(Project project) {
    def ext = project.extensions.create('countly', UploadSymbolsPluginExtension)
    project.tasks.register('uploadJavaSymbols') {
      group = "countly"
      description = "Upload Java minification mapping file mapping.txt to Countly server"

      // Resolve project/extension values at configuration time to avoid
      // capturing non-serializable Project reference in task actions
      def buildVersion = project.android.defaultConfig.versionName
      def appKey = ext.app_key
      def serverUrl = ext.server
      def noteJava = ext.noteJava
      def mappingFilePath = "${project.buildDir}/${ext.mappingFile}"

      if (!appKey || !serverUrl) {
        logger.warn("[Countly] uploadJavaSymbols: 'app_key' or 'server' is empty. " +
            "Make sure the countly block is configured before this task is realized. " +
            "Disabling task.")
        enabled = false
      }

      doLast {
        String url = serverUrl
        String path = "i/crash_symbols/upload_symbol"
        // Ensure there is exactly one "/" between the base URL and the path
        url = url.endsWith("/") ? url + path : url + "/" + path
        logger.debug("uploadJavaSymbols, Version name:[ {} ], Upload symbol url:[ {} ], Mapping file path:[ {} ]", buildVersion, url, mappingFilePath)
        File file = new File(mappingFilePath)
        if (!file.exists()) {
          logger.error("Mapping file not found")
          throw new StopActionException("Mapping file not found")
        }
        RequestBody formBody = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("symbols", file.getName(),
                RequestBody.create(MediaType.parse("text/plain"), file))
            .addFormDataPart("platform", "android")
            .addFormDataPart("app_key", appKey)
            .addFormDataPart("build", buildVersion)
            .addFormDataPart("note", noteJava)
            .build()
        Request request = new Request.Builder().url(url).post(formBody).build()

        if (request.body() != null) {
          logger.debug("uploadJavaSymbols, Generated request: {}", request.body().toString())
        } else {
          logger.error("uploadJavaSymbols, Request body is null which should not be the case")
        }

        OkHttpClient client = new OkHttpClient()
        Response response = client.newCall(request).execute()

        if (response.code() != 200) {
          if (response.body() != null) {
            logger.error("An error occurred while uploading the mapping file: {}", response.body().string())
          } else {
            logger.error("An error occurred while uploading the mapping file, response body null")
          }
        } else {
          logger.debug("File upload successful")
        }
      }
    }

    // Native symbols come from one task per application variant, fed by the libraries AGP merges for
    // that variant. Running the task builds those libraries first, so it can never dump a stale .so
    // that no longer matches the APK, and the directory includes what AARs contribute, such as
    // libcountly_native.so. AGP publishes it as SingleArtifact.MERGED_NATIVE_LIBS since 8.1; on older
    // versions, or outside an application module, a single task reads nativeObjectFilesDir as before.
    def components = project.extensions.findByName('androidComponents')
    def mergedNativeLibs = mergedNativeLibsArtifact(components)
    if (project.plugins.hasPlugin('com.android.application') && mergedNativeLibs != null) {
      def everyRelease = project.tasks.register('uploadNativeSymbols') {
        group = "countly"
        description = "Upload breakpad symbols of every release variant to Countly server"
      }
      components.onVariants(components.selector().all()) { variant ->
        String variantName = variant.name
        def upload = registerNativeUpload(project, ext, "uploadNativeSymbols${variantName.capitalize()}",
            variantName, variant.buildType ?: variantName,
            variant.artifacts.get(mergedNativeLibs), variant.outputs.first().versionName)
        if (variant.buildType == 'release') {
          everyRelease.configure { dependsOn upload }
        }
      }
    } else {
      if (components != null && mergedNativeLibs == null) {
        project.logger.warn("[Countly] This Android Gradle plugin predates SingleArtifact.MERGED_NATIVE_LIBS (AGP 8.1). " +
            "uploadNativeSymbols reads nativeObjectFilesDir and does not build the libraries first; run it right after assembling.")
      }
      registerNativeUpload(project, ext, 'uploadNativeSymbols', 'release', 'release', null, null)
    }
  }

  /**
   * Registers the upload of one variant's symbols. The configuration closure runs when the task is
   * realized, after the build script has run its countly block, so the extension is complete by then.
   * mergedLibs and variantVersionName are null on the fallback path, where AGP offers neither.
   */
  private static TaskProvider<UploadNativeSymbolsTask> registerNativeUpload(Project project, UploadSymbolsPluginExtension ext,
      String taskName, String variantName, String buildType, Provider<Directory> mergedLibs, Provider<String> variantVersionName) {
    project.tasks.register(taskName, UploadNativeSymbolsTask) { task ->
      task.description = "Upload breakpad symbols of the ${variantName} variant to Countly server"
      task.server.set(ext.server)
      task.appKey.set(ext.app_key)
      task.note.set(ext.noteNative)
      task.dumpSymsPath.set(ext.dumpSymsPath)
      task.workDir.set(project.layout.buildDirectory.dir("intermediates/countly/${variantName}"))

      String defaultVersionName = project.android.defaultConfig.versionName
      task.versionName.set(variantVersionName != null ? variantVersionName.orElse(defaultVersionName) : defaultVersionName)

      if (ext.nativeObjectFilesDir != null) {
        task.nativeLibs.set(project.layout.buildDirectory.dir(BreakpadSymbols.substituteBuildType(ext.nativeObjectFilesDir, buildType)))
      } else if (mergedLibs != null) {
        task.nativeLibs.set(mergedLibs)
      } else {
        task.nativeLibs.set(project.layout.buildDirectory.dir("intermediates/merged_native_libs/${buildType}"))
      }
    }
  }

  /** SingleArtifact.MERGED_NATIVE_LIBS, or null when this AGP is too old to have it. */
  private static Object mergedNativeLibsArtifact(Object components) {
    if (components == null) {
      return null
    }
    try {
      return Class.forName('com.android.build.api.artifact.SingleArtifact$MERGED_NATIVE_LIBS', true, components.class.classLoader)
          .getField('INSTANCE').get(null)
    } catch (ReflectiveOperationException ignored) {
      return null
    }
  }
}
