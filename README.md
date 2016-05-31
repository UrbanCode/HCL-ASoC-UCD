-To package the plugin for IBM Application Security on Cloud services: Run the 'scx' ant target in the build.xml file.

The releases directory contains packaged version of the plugin ready to use in UrbanCode Deploy.

If you don't have UrbanCode Deploy, you can use the predefined Ant targets 'start_ma_scan_scx', 'start_dast_scan_scx', 'start_sast_scan_scx' and 'start_ios_scan_scx'

Use your IBM Id and password as 'loginusername' and 'loginpassword' properties.

Rescans: Set the 'parentScanId' property to the original scan ID.