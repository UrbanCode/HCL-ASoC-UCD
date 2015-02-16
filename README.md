In order to package the plugin for Bluemix services run the 'bluemix' ant target in the build.xml file.
In order to package the plugin for Smartcloud Exchange services run the 'scx' ant target in the build.xml file.

In the releases directory you can find packaged versions of the plugin ready to use in UrbanCode Deploy.

In case you don't have UrbanCode Deploy you can use the predefined Ant targets 'start_ma_scan_bluemix', 'start_dast_scan_bluemix' or 'start_ma_scan_scx'.
For the Bluemix targets please use your Binding id and password as 'loginusername' and 'loginpassword' properties.
For the Smart Cloud target please use your IBM Id and password as 'loginusername' and 'loginpassword' properties.

For rescans set the 'parentScanId' property to the original scan id.