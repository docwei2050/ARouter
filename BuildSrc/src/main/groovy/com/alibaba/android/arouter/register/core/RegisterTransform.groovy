package com.alibaba.android.arouter.register.core

import com.alibaba.android.arouter.register.utils.Logger
import com.alibaba.android.arouter.register.utils.ScanSetting
import com.alibaba.android.arouter.register.utils.ScanUtil
import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.gradle.api.Project

/**
 * transform api
 * <p>
 *     1. Scan all classes to find which classes implement the specified interface
 *     2. Generate register code into class file: {@link ScanSetting#GENERATE_TO_CLASS_FILE_NAME}
 * @author billy.qi email: qiyilike@163.com
 * @since 17/3/21 11:48
 */
class RegisterTransform extends Transform {

    Project project
    static ArrayList<ScanSetting> registerList
    static File fileContainsInitClass;

    RegisterTransform(Project project) {
        this.project = project
    }

    /**
     * name of this transform
     * @return
     */
    @Override
    String getName() {
        return ScanSetting.PLUGIN_NAME
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        //关心的是字节码
        return TransformManager.CONTENT_CLASS
    }

    /**
     * The plugin will scan all classes in the project
     * @return
     */
    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    boolean isIncremental() {
        return false
    }


    @Override
    void transform(Context context, Collection<TransformInput> inputs
                   , Collection<TransformInput> referencedInputs
                   , TransformOutputProvider outputProvider
                   , boolean isIncremental) throws IOException, TransformException, InterruptedException {

        Logger.i('Start scan register info in jar file.')

        long startTime = System.currentTimeMillis()
        boolean leftSlash = File.separator == '/'

        inputs.each { TransformInput input ->

            // scan all jars
            input.jarInputs.each { JarInput jarInput ->
                String destName = jarInput.name
                Logger.i('destName处理前-->'+destName)
                // rename jar files
                def hexName = DigestUtils.md5Hex(jarInput.file.absolutePath)
                if (destName.endsWith(".jar")) {
                    destName = destName.substring(0, destName.length() - 4)
                }
                Logger.i('destName处理后-->'+destName)
                //先交代源文件和目的文件
                // input file
                File src = jarInput.file
                // output file 这只是jar文件名，不是file文件名啊
                File dest = outputProvider.getContentLocation(destName + "_" + hexName, jarInput.contentTypes, jarInput.scopes, Format.JAR)

                //scan jar file to find classes
                if (ScanUtil.shouldProcessPreDexJar(src.absolutePath)) {
                    ScanUtil.scanJar(src, dest)
                }
                FileUtils.copyFile(src, dest)

            }
            // scan class files
            input.directoryInputs.each { DirectoryInput directoryInput ->
                File dest = outputProvider.getContentLocation(directoryInput.name, directoryInput.contentTypes, directoryInput.scopes, Format.DIRECTORY)
                String root = directoryInput.file.absolutePath
                Logger.i('root->文件夹directoryInput.file.absolutePath--》.'+root)
                if (!root.endsWith(File.separator))
                    root += File.separator

                Logger.i('directoryInput.file--》是.'+directoryInput.file)
                Logger.i('开始遍历文件夹中file.eachFileRecurse')
                directoryInput.file.eachFileRecurse { File file ->
                    def path = file.absolutePath.replace(root, '')
                    Logger.i('path-->'+path)
                    Logger.i('file-->'+file)
                    if (!leftSlash) {
                        path = path.replaceAll("\\\\", "/")
                    }
                    if(file.isFile() && ScanUtil.shouldProcessClass(path)){
                        ScanUtil.scanClass(file)
                    }
                }
                Logger.i('遍历结束--》copy文件夹-->'+directoryInput.file+"到"+"dest")
                // copy to dest
                FileUtils.copyDirectory(directoryInput.file, dest)
            }
        }

        Logger.i('Scan finish, current cost time ' + (System.currentTimeMillis() - startTime) + "ms")

        if (fileContainsInitClass) {
            registerList.each { ext ->
                Logger.i('开始插入代码Insert register code to file ' + fileContainsInitClass.absolutePath)

                if (ext.classList.isEmpty()) {
                    Logger.e("没有类实现找到No class implements found for interface:" + ext.interfaceName)
                } else {
                    ext.classList.each {
                        Logger.i(it)
                    }
                    RegisterCodeGenerator.insertInitCodeTo(ext)
                }
            }
        }

        Logger.i("Generate code finish, current cost time: " + (System.currentTimeMillis() - startTime) + "ms")
    }
}
