includeTargets << grailsScript("Init")
includeTargets << grailsScript("_GrailsEvents")
includeTargets << grailsScript("_GrailsArgParsing")

// System environment JAVA_HOME
JAVA_HOME = ant.project.properties."environment.JAVA_HOME"

// full schemagen.exe path
schemagenExe = new File("${JAVA_HOME}/bin/schemagen.exe")

// default domain class directory
domainDir = new File("${grailsSettings.baseDir}/grails-app/domain")

// directory where we do work
workDir = new File("${grailsSettings.projectWorkDir}/domain-schemagen")

// temp jar output filename
tempJar = new File("${workDir}/temp.jar")

// default location generated schemas are created
workSchemaDir = new File("${workDir}/schemas")

// arbitrary name for default package. hopefully it's not the same as any of their packages
defaultPackageName = "schemagenDomainRoot"

// define ant task
ant.taskdef (name : 'groovyc', classname : 'org.codehaus.groovy.ant.Groovyc')

/**
 * The Default target
 */
target(main: "Default Target") {
    depends(checkEnv,parseArguments)

    outputDir = argsMap["outputDir"] ?: "${grailsSettings.baseDir}/web-app/schemas"
    pkg = argsMap["pkg"]
    classes = argsMap["classes"]

    if(argsMap["help"]) {
        domainSchemaHelp()
    } else {
        domainSchemagen()
    }
}

target(domainSchemagen: "Generates schemas with schemagen from grails domain classes") {

    depends(cleanWork,compileDomain,jarDomain)

    Map<String,List<String>> packagedClasses = buildClassPackageMap(new File("${domainDir}"))

    packagedClasses.each {
        if(!pkg || it.key in ((String)pkg)?.split(",")) {

            // fully qualified classes in package
            List<String> fullyQualifiedClasses = buildFullyQualifiedClassNames(it)

            // string of all fully qualified classes in package, delimited by space
            line = ""
            fullyQualifiedClasses.each {
                if(!classes || it)
                line += it + " ";
            }

            // exec schemagen
            if(!"".equals(line)) {

                event("StatusUpdate",["Generating schema(s) for package: '" + it.key + "' containing classes: " + it.value])

                ant.exec(executable : "${schemagenExe}", dir : "${workDir}", output: null) {
                    arg(line : "-classpath temp.jar ${line} -d ${workDir}/schemas/")
                }

                filename = defaultPackageName.equals(it.key) ? "defaultPackage" : it.key

                ant.move(file: "${workSchemaDir}", toDir: "${outputDir}/${filename.replace(".","/")}")
            }

        }

    }

    cleanWork()

    event("StatusFinal", ["Schema(s) created, you can find them here: ${new File(outputDir)}"])

}

target(checkEnv: "Checks to see if JAVA_HOME is set") {
    if("".equals(JAVA_HOME)) {
        event("StatusError",["JAVA_HOME environment variable not set."])
    }
}

target(cleanWork: "Cleans up") {
    ant.delete(dir: workDir)
}

target(compileDomain: "Compiles the domain classes") {

    ant.mkdir(dir : workSchemaDir)
    groovyc(srcdir : domainDir, destdir : workDir)

}

target(jarDomain: "Jar the domain classes so we can run schemagen") {
    ant.jar(basedir: workDir, destfile: tempJar)
}

target(domainSchemaHelp: "Prints the help message") {
	println '''\

Grails domain-schemagen Plugin

Available Options:

-outputDir=      Override the default output directory.
-pkg=        Specify specific packages for schemagen, multiples separated by comma.
NYI -classes=    Specify specific classes for schemagen, multiples separated by comma. Must be fully qualified class names if not used with -pkg.
NYI -srcDir=     Specify alternate directory for Groovy source. Yes, that means you're not limited to Domain classes!
'''

}

setDefaultTarget("main")

/**
 * Builds a Map, keyed by package name, valued by classes within said package, of all
 * packages and classes within the Directory provided.
 *
 * @param f Root Directory, aka grails domain folder
 * @return Map keyed by package with classes as value
 */
private Map<String,List<String>> buildClassPackageMap(File f) {
    return traverseDirectories(f,new LinkedHashMap<String, List<String>>())
}

/**
 * Builds a list of fully qualified class names within a package
 * @param entry Map Entry from a map keyed by package name, valued by classes
 * @return
 */
private List<String> buildFullyQualifiedClassNames(Map.Entry<String,List<String>> entry) {

    List<String> classNames = new ArrayList<String>()

    if(entry.value.size() > 0) {
        if(defaultPackageName.equals(entry.key)) {
            return entry.value
        } else {
            entry.value.each {
                classNames.add(entry.key + "." + it)
            }
        }
    }

    return classNames

}

/**
 * Note: This method is recursive
 *
 * Recursively checks directories for classes, and adds list of classes to a map keyed by package name.
 *
 * @param f Directory to traverse
 * @param map Map to populate
 * @return fully populated Map keyed by package with classes as value
 */
private Map<String,List<String>> traverseDirectories(File f, Map<String,List<String>> map) {

    if(f.isDirectory()) {

        String path = f.absolutePath.replace("${domainDir}","").replace("\\", "/")

        if("".equals(path)) {
            path = defaultPackageName
        } else {
            path = path.substring(1).replace("/",".")
        }

        map.put(path, getClassesForDirectory(f))

    }

    f.listFiles().each {
        if(it.isDirectory()) {
             traverseDirectories(it,map)
        }
    }

    return map

}

/**
 * @param f directory to check
 * @return a list of all classes within a given directory
 */
private List<String> getClassesForDirectory(File f) {

    if(!f.isDirectory()) {
        throw new RuntimeException("Not a directory")
    }

    List<String> classes = new ArrayList<String>()

    f.listFiles().each {
        if(it.isFile()) {
            classes.add(it.name.replace(".groovy","").replace(".java",""))
        }
    }

    return classes

}