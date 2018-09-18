# IBM ASoC plug-in for IBM UrbanCode Deploy
---
Note: This is not the plugin distributable! This is the source code. To find the installable plugin, go to the plug-in page on the [UrbanCode Plug-ins microsite](https://developer.ibm.com/urbancode/plugins).

### License
This plug-in is protected under the [Eclipse Public 1.0 License](http://www.eclipse.org/legal/epl-v10.html)

### Overview
IBM Application Security on Cloud is an application security offering that allows you to scan
on prem, web, and mobile applications for security vulnerabilities. The plugin allows you to 
run all supported types of scans and manage ASoC presences. Presences allow you to run
scans on apps not connected to the internet or require a proxy server to make a connection.

### Documentation
All plug-in documentation is updated and maintained on the [UrbanCode Plug-ins microsite](https://developer.ibm.com/urbancode/plugins).

### Support
Plug-ins downloaded directly from the [UrbanCode Plug-ins microsite](https://developer.ibm.com/urbancode/plugins) are fully supported by HCL. Create a GitHub Issue or Pull Request for minor requests and bug fixes. For time sensitive issues that require immediate assistance, contact the support team directly. Plug-ins built externally or modified with custom code are supported on a best-effort-basis using GitHub Issues.

### Locally Build the Plug-in
This open source plug-in uses Gradle as its build tool. [Install the latest version of Gradle](https://gradle.org/install) to build the plug-in locally. Build the plug-in by running the `gradle` command in the plug-in's root directory. The plug-in distributable will be placed under the `build/distributions` folder.
