-To package the plugin for Bluemix services: Run the 'bluemix' ant target in the build.xml file.
-To package the plugin for IBM Application Security on Cloud services: Run the 'scx' ant target in the build.xml file.

The releases directory contains packaged versions of the plugin ready to use in UrbanCode Deploy.

If you don't have UrbanCode Deploy, you can use the predefined Ant targets 'start_ma_scan_bluemix', 'start_dast_scan_bluemix', 'start_sast_scan_bluemix' or 'start_ma_scan_scx', 'start_dast_scan_scx', 'start_sast_scan_scx'.

Bluemix target: Use your Binding ID and password as 'loginusername' and 'loginpassword' properties.
IBM Application Security on Cloud target: Use your IBM Id and password as 'loginusername' and 'loginpassword' properties.

Rescans: Set the 'parentScanId' property to the original scan ID.