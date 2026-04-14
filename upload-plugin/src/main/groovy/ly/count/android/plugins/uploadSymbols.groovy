package ly.count.android.plugins

import okhttp3.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.StopActionException

import static groovy.io.FileType.FILES

class UploadSymbolsPluginExtension {
  String app_key = ""
  String server = ""
  String mappingFile = "outputs/mapping/release/mapping.txt"
  String dumpSymsPath = "/usr/bin"
  String nativeObjectFilesDir = "intermediates/cmake/release/obj"
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

    project.tasks.register('uploadNativeSymbols') {
      group = "countly"
      description = "Upload breakpad symbols folder to Countly server"

      // Resolve project/extension values at configuration time to avoid
      // capturing non-serializable Project reference in task actions
      def buildVersion = project.android.defaultConfig.versionName
      def appKey = ext.app_key
      def serverUrl = ext.server
      def noteNative = ext.noteNative
      def dumpSymsPath = ext.dumpSymsPath
      def objectsDirPath = "${project.buildDir}/${ext.nativeObjectFilesDir}"
      def countlyDirStr = "${project.buildDir}/intermediates/countly"

      if (!appKey || !serverUrl) {
        logger.warn("[Countly] uploadNativeSymbols: 'app_key' or 'server' is empty. " +
            "Make sure the countly block is configured before this task is realized. " +
            "Disabling task.")
        enabled = false
      }

      doLast {
        String url = "${serverUrl}/i/crash_symbols/upload_symbol"
        String breakpadVersion = "$dumpSymsPath/dump_syms --version".execute().getText().trim()

        if (!(breakpadVersion =~ /^\d+\.\d+\+cly$/)) {
          breakpadVersion = "0.1+bpd"
        }

        def objectsDir = new File(objectsDirPath)
        def countlyDir = new File(countlyDirStr)
        logger.debug("uploadNativeSymbols, Version name:[ {} ], Upload symbol url:[ {} ], objectsDir:[ {} ], countlyDirStr:[ {} ], countlyDir:[ {} ], breakpadVersion:[ {} ]", buildVersion, url, objectsDir, countlyDirStr, countlyDir, breakpadVersion)

        countlyDir.deleteDir()
        countlyDir.mkdirs()
        def filterObjectFiles = ~/.*\.so$/
        def i = 0
        def processFile = {
          i = i + 1
          def cmd = "$dumpSymsPath/dump_syms $it"
          println cmd
          def proc = cmd.execute()
          def outputStream = new StringBuffer()
          def currentSymbolFile = new File("$countlyDirStr/current_$i")
          proc.waitForProcessOutput(outputStream, System.err)
          BufferedWriter bwr = new BufferedWriter(new FileWriter(currentSymbolFile))
          bwr.write(outputStream.toString())
          bwr.flush()
          bwr.close()
          def line = ""
          currentSymbolFile.withReader { line = it.readLine() }
          def words = line.split()
          File symbolDir = new File("$countlyDirStr/symbols/${words[-1]}/${words[-2]}")
          println symbolDir
          symbolDir.mkdirs()
          File newFile = new File("$symbolDir/${words[-1]}.sym")
          newFile << currentSymbolFile.text
          currentSymbolFile.delete()
        }
        objectsDir.traverse type: FILES, visit: processFile, nameFilter: filterObjectFiles
        def tarFileName = "$countlyDirStr/symbols.tar.gz"
        // Use standalone AntBuilder instead of project.ant for configuration cache compatibility
        new groovy.ant.AntBuilder().tar(destfile: tarFileName, basedir: "$countlyDirStr", includes: "symbols/**", compression: "gzip")
        File file = new File(tarFileName)
        RequestBody formBody = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("symbols", file.getName(),
                RequestBody.create(MediaType.parse("text/plain"), file))
            .addFormDataPart("platform", "android_native")
            .addFormDataPart("app_key", appKey)
            .addFormDataPart("build", buildVersion)
            .addFormDataPart("note", noteNative)
            .addFormDataPart("sym_tool_ver", breakpadVersion)
            .build()
        Request request = new Request.Builder().url(url).post(formBody).build()
        logger.debug("uploadNativeSymbols, Generated request: {}", request.body().toString())

        OkHttpClient client = new OkHttpClient()
        Response response = client.newCall(request).execute()

        if (response.code() != 200) {
          logger.error("uploadNativeSymbols, An error occurred while uploading the symbols folder: {}", response.body().string())
        } else {
          logger.debug("uploadNativeSymbols, File upload successful")
        }
      }
    }
  }
}