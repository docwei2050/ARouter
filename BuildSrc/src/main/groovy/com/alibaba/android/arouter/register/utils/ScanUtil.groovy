package com.alibaba.android.arouter.register.utils

import com.alibaba.android.arouter.register.core.RegisterTransform
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes

import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * Scan all class in the package: com/alibaba/android/arouter/
 * find out all routers,interceptors and providers
 * @author billy.qi email: qiyilike@163.com
 * @since 17/3/20 11:48
 */
class ScanUtil {

    /**
     * scan jar file
     * @param jarFile All jar files that are compiled into apk
     * @param destFile dest file after this transform
     */
    static void scanJar(File jarFile, File destFile) {
        if (jarFile) {
            //跟找dexFile一样啊
            def file = new JarFile(jarFile)
            Enumeration enumeration = file.entries()
            while (enumeration.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) enumeration.nextElement()
                String entryName = jarEntry.getName()
                if (entryName.startsWith(ScanSetting.ROUTER_CLASS_PACKAGE_NAME)) {
                    //com/alibaba/android/arouter/routes/

                    //我要找的文件 com.alibaba.android.arouter.routes.ARouter$$Root$$app
                    //io流
                    InputStream inputStream = file.getInputStream(jarEntry)
                    scanClass(inputStream)
                    inputStream.close()
                } else if (ScanSetting.GENERATE_TO_CLASS_FILE_NAME == entryName) {
                    //com/alibaba/android/arouter/core/LogisticsCenter .class

                    // mark this jar file contains LogisticsCenter.class
                    // After the scan is complete, we will generate register code into this file
                    //为什么只在jar里面去找LogisticsCenter.class，，那文件夹那种方式是不是就找不到了
                    RegisterTransform.fileContainsInitClass = destFile
                    Logger.i('在jar里面找到了LogisticsCenter.class'+destFile);
                }
            }
            file.close()
        }
    }

    static boolean shouldProcessPreDexJar(String path) {
        return !path.contains("com.android.support") && !path.contains("/android/m2repository")
    }

    static boolean shouldProcessClass(String entryName) {
        return entryName != null && entryName.startsWith(ScanSetting.ROUTER_CLASS_PACKAGE_NAME)
    }

    /**
     * scan class file
     * @param class file
     */
    static void scanClass(File file) {
        scanClass(new FileInputStream(file))
    }

    static void scanClass(InputStream inputStream) {
        //ClassReader和ClassWriter是ASM的api
        ClassReader cr = new ClassReader(inputStream)
        ClassWriter cw = new ClassWriter(cr, 0)
        //他的方法只是给ScanSetting
        ScanClassVisitor cv = new ScanClassVisitor(Opcodes.ASM5, cw)
        cr.accept(cv, ClassReader.EXPAND_FRAMES)
        inputStream.close()
    }

    static class ScanClassVisitor extends ClassVisitor {

        ScanClassVisitor(int api, ClassVisitor cv) {
            super(api, cv)
        }
        //Visits the header of the class 访问类头
        void visit(int version, int access, String name, String signature,
                   String superName, String[] interfaces) {
            //interfaces就是这个类的接口  ARouter$$Root$$app 的接口是IRouteRoot
            // 【public class ARouter$$Root$$app implements IRouteRoot】
            super.visit(version, access, name, signature, superName, interfaces)
            RegisterTransform.registerList.each { ext ->
                Logger.i('ext就是ScanSetting');
                if (ext.interfaceName && interfaces != null) {
                    //com/alibaba/android/arouter/routes/
                    //我要找的文件 com.alibaba.android.arouter.routes.ARouter$$Root$$app
                    interfaces.each { itName ->
                        Logger.i('interfaces 子itName---》.'+itName)
                        if (itName == ext.interfaceName) {
                            //fix repeated inject init code when Multi-channel packaging
                            if (!ext.classList.contains(name)) {
                                ext.classList.add(name)
                                Logger.i(' ext.classList.add(name)-添加到子类里面去--》.'+name)
                            }
                        }
                    }
                }
            }
        }
    }

}