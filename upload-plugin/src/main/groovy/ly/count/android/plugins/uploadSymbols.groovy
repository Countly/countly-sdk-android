package ly.count.android.plugins

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.StopActionException
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.GradleException
import static groovy.io.FileType.*
import static groovy.io.FileVisitResult.*

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.MultipartBody;
import okhttp3.MediaType


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
        OkHttpClient client = null
        Request request = null
        def ext = project.extensions.create('countly', UploadSymbolsPluginExtension)
        project.task('uploadJavaSymbols') {
            group = "countly"
            description = "Upload Java minification mapping file mapping.txt to Countly server"
            doFirst {
                if (!ext.app_key) {
                    logger.error("Please specify your app key in countly block.")
                    throw new StopExecutionException("Please specify your app key in countly block.")
                }
                if (!ext.server) {
                    logger.error("Please specify your server in countly block.")
                    throw new StopExecutionException("Please specify your server in countly block.")
                }
                String buildVersion = project.android.defaultConfig.versionName
                String url = "${ext.server}/i/crash_symbols/upload_symbol"
                // println "URL = $url"
                def filePath = "$project.buildDir/$ext.mappingFile"
                // logger.debug("mapping file to be uploaded = {}", filePath)
                File file = new File(filePath)
                if (!file.exists()) {
                    logger.error("Mapping file not found")
                    throw new StopActionException("Mapping file not found")
                }
                RequestBody formBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("symbols", file.getName(),
                        RequestBody.create(MediaType.parse("text/plain"), file))
                        .addFormDataPart("platform", "android")
                        .addFormDataPart("app_key", ext.app_key)
                        .addFormDataPart("build", buildVersion)
                        .addFormDataPart("note", ext.noteJava)
                        .build()
                request = new Request.Builder().url(url).post(formBody).build()
                // println("request constructed")
            }
            doLast {
                client = new OkHttpClient()
                Response response = client.newCall(request).execute();
                def result = response.body().string()
                // println response.code()
                // println result
                if (response.code != 200) {
                    logger.error("An error occured while uploading the mapping file: {}", result)
                    // throw new GradleException("An error occured while uploading the symbol file: " + logger.error(response.body().string()))
                }
                else {
                    logger.debug("File upload successful")
                }
                // println "DONE"
            }
        }

        project.task('uploadNativeSymbols') {
            group = "countly"
            description = "Upload breakpad symbols folder to Countly server"
            doFirst {
                String buildVersion = project.android.defaultConfig.versionName
                String url = "${ext.server}/i/crash_symbols/upload_symbol"
                String breakpadVersion = "$ext.dumpSymsPath/dump_syms --version".execute().getText().trim()
                // println breakpadVersion

                if (!(breakpadVersion =~ /^\d+\.\d+\+cly$/)) {
                    breakpadVersion = "0.1+bpd"
                }

                def objectsDir = new File("$project.buildDir/$ext.nativeObjectFilesDir")
                def countlyDirStr = "$project.buildDir/intermediates/countly"
                def countlyDir = new File("$countlyDirStr")
                countlyDir.deleteDir()
                countlyDir.mkdirs()
                // println "objectsDir=$objectsDir"
                def filterObjectFiles = ~/.*\.so$/
                def i = 0
                def processFile = {
                    i = i+1
                    def cmd = "$ext.dumpSymsPath/dump_syms $it"
                    println cmd
                    def proc = cmd.execute()
                    def outputStream = new StringBuffer();
                    def currentSymbolFile = new File("$countlyDirStr/current_$i")
                    proc.waitForProcessOutput(outputStream, System.err);
                    BufferedWriter bwr = new BufferedWriter(new FileWriter(currentSymbolFile));
                    bwr.write(outputStream.toString())
                    bwr.flush()
                    bwr.close()
                    // println it
                    def line
                    currentSymbolFile.withReader { line = it.readLine() }
                    // println line
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
                project.ant.tar(destfile: tarFileName, basedir: "$countlyDirStr", includes: "symbols/**", compression: "gzip")
                File file = new File(tarFileName)
                RequestBody formBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("symbols", file.getName(),
                        RequestBody.create(MediaType.parse("text/plain"), file))
                        .addFormDataPart("platform", "android_native")
                        .addFormDataPart("app_key", ext.app_key)
                        .addFormDataPart("build", buildVersion)
                        .addFormDataPart("note", ext.noteNative)
                        .addFormDataPart("sym_tool_ver", breakpadVersion)
                        .build()
                request = new Request.Builder().url(url).post(formBody).build();
            }
            doLast {
                client = new OkHttpClient()
                Response response = client.newCall(request).execute();
                def result = response.body().string()
                // println response.code()
                // println result
                if (response.code != 200) {
                    logger.error("An error occured while uploading the symbols folder: {}", result)
                    // throw new GradleException("An error occured while uploading the symbol file: " + logger.error(response.body().string()))
                }
                else {
                    logger.debug("File upload successful")
                }
            }
        }
    }
}