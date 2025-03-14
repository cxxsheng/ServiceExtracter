package com.cxxsheng;

import com.cxxsheng.util.JsonUtils;
import com.cxxsheng.util.Logger;
import soot.*;
import soot.options.Options;

import java.util.ArrayList;
import java.util.List;

public class Main {


    private static class MethodPair {
        public String interfaceMethod;
        public String implementationMethod;

        public MethodPair(String interfaceMethod, String implementationMethod) {
            this.interfaceMethod = interfaceMethod;
            this.implementationMethod = implementationMethod;
        }
    }

    private static class ServiceInfo {
        public String interfaceName;
        public String implementationName;
        public List<MethodPair> methods;

        public ServiceInfo(String interfaceName, String implementationName) {
            this.interfaceName = interfaceName;
            this.implementationName = implementationName;
            this.methods = new ArrayList<>();
        }
    }



//    private static final String TARGET_CLASS = "com.android.server.notification.NotificationManagerService";
    private static final String FRAMEWORK = "/Users/cxxsheng/someFramework/aosp14/framework.apk";

    private static final  String androidJarPath = "/Users/cxxsheng/Library/Android/sdk/platforms/android-34/android.jar";

    private static final String IINTERFACE = "android.os.IInterface";

    private static final List<String> SKIP_MATCH_METHODS = new ArrayList<>();

    // make sure class has this interface, for instance: class a implement interfaceClass; class b extends a;
    // class b should be filtered out
    private static List<SootClass> enforceHasInterface(List<SootClass> classes, SootClass interfaceClass){
        List<SootClass> filterClasses = new ArrayList<>();
        for (SootClass sootClass : classes){
            if(sootClass.getInterfaces().contains(interfaceClass)){
                filterClasses.add(sootClass);
            }
        }
        return filterClasses;
    }

    private static void startAnalysis() {
        List<ServiceInfo> services = new ArrayList<>();



        SootClass iInterface = Scene.v().getSootClass(IINTERFACE);

        Hierarchy hierarchy = Scene.v().getActiveHierarchy();
        //managerInterface is the first layer of aidl wrapped class
        List<SootClass> managerInterfaces = hierarchy.getSubinterfacesOf(iInterface);
        for (SootClass managerInterface : managerInterfaces) {
            List<SootClass> allImps =  hierarchy.getDirectImplementersOf(managerInterface);
            List<SootClass> filterImps = enforceHasInterface(allImps, managerInterface);

            SootClass defaultClass = null;
            SootClass proxyClass = null;
            SootClass stubClass = null;

            for (SootClass filterImp : filterImps) {
                if (filterImp.getName().endsWith("$Default"))
                    defaultClass = filterImp;
                else if (filterImp.getName().endsWith("$Stub$Proxy"))
                    proxyClass = filterImp;
                else if (filterImp.getName().endsWith("$Stub"))
                    stubClass = filterImp;
            }

            Logger.info("Found default/proxy/stub: " + managerInterface + ":\n\t" + (defaultClass != null ? defaultClass.getName() : "null") + " | " + (proxyClass != null ? proxyClass.getName() : "null") + " | " + (stubClass != null ? stubClass.getName() : "null"));

//             default ？
            if (defaultClass != null && !hierarchy.getDirectSubclassesOf(defaultClass).isEmpty())
                Logger.info("There are some default subclasses: " + hierarchy.getDirectSubclassesOf(defaultClass));

            if (proxyClass != null && stubClass != null) {

                List<SootClass> mayRealServices = hierarchy.getDirectSubclassesOf(stubClass);
                if (mayRealServices.size() == 1){
                    SootClass realService = mayRealServices.getFirst();
                    if (realService.isAbstract())
                    {
                        Logger.info("realService is abstract: " + realService);
                    }else {
                        ServiceInfo serviceInfo = new ServiceInfo(
                                managerInterface.getName(),
                                realService.getName()
                        );

                        List<SootMethod> managerMethods = managerInterface.getMethods();
                        for (SootMethod managerMethod : managerMethods) {
                            String methodSubSignature = managerMethod.getSubSignature();

                            if(SKIP_MATCH_METHODS.contains(methodSubSignature))
                                continue;

                            SootMethod targetMethod = realService.getMethod(methodSubSignature);

                            if (targetMethod.toString().contains("android.os.Bundle")){
                                Logger.info("TargetMethod has a Bundle Param: " + managerMethod + "/" + targetMethod);
                            }

                            serviceInfo.methods.add(new MethodPair(
                                    managerMethod.toString(),
                                    targetMethod.toString()
                            ));

                        }
                        services.add(serviceInfo);
                    }

                }else if (mayRealServices.isEmpty()){
                    //do nothing
                    Logger.info("Can't find any implementations for " + managerInterface);
                }else {
                    Logger.info("More than one proxy implementation found for " + managerInterface.getName() + ", list: " + mayRealServices);
                }

            }

        }

        JsonUtils.writeToJsonFile(services, "services_analysis.json");

    }

    private static void initializeSoot(String jarTargetPath) {


        //        String sb = Scene.v().getAndroidJarPath(androidJarPath, jarPath);
        Options.v().set_src_prec(Options.src_prec_apk);
        List<String> processPathList = new ArrayList<>();
        processPathList.add(FRAMEWORK);
        processPathList.add(jarTargetPath);

        Options.v().set_process_dir(processPathList);
        Options.v().set_force_android_jar(androidJarPath);
        Options.v().set_process_multiple_dex(true);


        //        Options.v().set_output_dir("out/");
        Options.v().set_output_format(Options.output_format_jimple);


        Options.v().set_allow_phantom_refs(true);
        Options.v().set_whole_program(true);
        Options.v().set_keep_line_number(false);
        Options.v().set_wrong_staticness(Options.wrong_staticness_ignore);
        Options.v().set_debug(false);
        Options.v().set_verbose(false);
        Options.v().set_validate(false);
        Scene.v().loadNecessaryClasses();

        Scene.v().loadNecessaryClasses();// may take dozens of seconds

    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java Main <jar-path> <class-name>");
            return;
        }
        String jarPath = args[0];
//        String className = args[1];
        initParams();
        initializeSoot(jarPath);
        startAnalysis();
//        forTest();
    }

    private static void initParams() {
        SKIP_MATCH_METHODS.add("void <clinit>()");
    }

    private static void forTest(){
        SootClass INotificationManager = Scene.v().getSootClass("android.app.INotificationManager");
        Hierarchy hierarchy = Scene.v().getActiveHierarchy();
//        List<SootClass> sootClasses = hierarchy.getDirectSubclassesOf(INotificationManager);
        SootClass a = Scene.v().getSootClass("com.android.server.notification.NotificationManagerService$11");
        System.out.println();
    }
}