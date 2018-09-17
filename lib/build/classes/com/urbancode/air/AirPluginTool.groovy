/*
* Licensed Materials - Property of IBM* and/or HCL**
* UrbanCode Deploy
* UrbanCode Build
* UrbanCode Release
* AnthillPro
* (c) Copyright IBM Corporation 2011, 2017. All Rights Reserved.
* (c) Copyright HCL Technologies Ltd. 2018. All Rights Reserved.
*
* U.S. Government Users Restricted Rights - Use, duplication or disclosure restricted by
* GSA ADP Schedule Contract with IBM Corp.
*
* * Trademark of International Business Machines
* ** Trademark of HCL Technologies Limited
*/
package com.urbancode.air;

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayInputStream;



public class AirPluginTool {

    //**************************************************************************
    // CLASS
    //**************************************************************************
    static final private String UCD_ENCRYPT_PROPERTIES_ENV_VAR = "UCD_USE_ENCRYPTED_PROPERTIES";
    static final private String UCD_SECRET_VAR = "ucd.properties.secret";

    //**************************************************************************
    // INSTANCE
    //**************************************************************************
    
    final public def isWindows = (System.getProperty('os.name') =~ /(?i)windows/).find()

    def out = System.out;
    def err = System.err;

    private def inPropsFile;
    private def outPropsFile;

    private def outProps;
    private def hasReadStdIn = false;
    private def secret = null;

    public AirPluginTool(def inFile, def outFile){
        inPropsFile = inFile;
        outPropsFile = outFile;
        outProps = new Properties();
    }

    public Properties getStepProperties() {
        return getStepProperties(getEncKey());
    }

    public Properties getStepProperties(String encSecret) {
        byte[] sec = decodeSecret(encSecret);
        Properties props = new Properties();
        InputStream inStream = new FileInputStream(inPropsFile);
        if (sec == null) {
            loadProperties(props, inStream);
        }
        else {
            def secret = newSecretContainer(sec);
            loadEncryptedProperties(secret, props, inStream);
        }
        return props;
    }
    
    private void loadEncryptedProperties(def secret, Properties props, InputStream inStream) {
        try {
            def blob = fromEncryptedBytes(secret, inStream);
            loadProperties(props, new ByteArrayInputStream(blob.get()));
        }
        finally {
            close(inStream);
        }
    }
    
    private void loadProperties(Properties props, InputStream inStream) {
        try {
            props.load(inStream);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        finally {
             close(inStream);
        }
    }

    public void setOutputProperty(String name, String value) {
        this.outProps.setProperty(name, value);
    }

    public String getEncKey() {
        if (!hasReadStdIn) {
            boolean useEnc = Boolean.valueOf(System.getenv(UCD_ENCRYPT_PROPERTIES_ENV_VAR));
            Properties props = new Properties();
            if (useEnc) {
                props.load(System.in);
                this.secret = props.getProperty(UCD_SECRET_VAR);
                hasReadStdIn = true;
            }
        }
        return this.secret;
    }

    public byte[] decodeSecret(String secret) {
        if (secret != null && !secret.trim().equals("")) {
            return getBase64Codec().decodeFromString(secret);
        }
        return null;
    }
    
    public void setOutputProperties(String encSecret) {
        byte[] sec = decodeSecret(encSecret);
        final OutputStream os = new FileOutputStream(this.outPropsFile);
        if (sec == null) {
            writePropertiesFile(outProps, os);
        }
        else {
            def secretCon = newSecretContainer(sec);
            writeEncryptedPropertiesFile(secretCon, outProps, os);
        }
    }

    public void setOutputProperties() {
        setOutputProperties(getEncKey());
    }
    
    private void writeEncryptedPropertiesFile(def secret, Properties props, OutputStream os) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writePropertiesFile(props, baos);
        try {
            def blob = fromUnencryptedBytes(secret, baos.toByteArray());
            close(baos);
            os.write(blob.getEncryptedBytes());
        }
        finally {
            close(os);
        }
    }
    
    private void writePropertiesFile(Properties props, OutputStream os) {
        try {
            props.store(os, "");
        }
        finally {
            close(os);
        }
    }

    public String getAuthToken() {
        String authToken = System.getenv("AUTH_TOKEN");
        return "{\"token\" : \"" + authToken + "\"}";
    }

    public String getAuthTokenUsername() {
        return "PasswordIsAuthToken";
    }

    public void storeOutputProperties() {
        setOutputProperties();
    }

    private def getBase64Codec() {
        return this.class.classLoader.loadClass("com.urbancode.air.securedata.Base64Codec").newInstance();
    }

    private def fromUnencryptedBytes(def secret, byte[] os) {
        def secBlobClass = this.class.classLoader.loadClass("com.urbancode.air.securedata.SecureBlob");
        def secConClass = this.class.classLoader.loadClass("com.urbancode.air.securedata.SecretContainer");
        def fromUncMeth = secBlobClass.getMethod("fromUnencryptedBytes", secConClass, byte[].class);
        return fromUncMeth.invoke(null, secret, os);
    }

    private def fromEncryptedBytes(def secret, InputStream is) {
        def ioClass = this.class.classLoader.loadClass("com.urbancode.commons.util.IO");
        def readMeth = ioClass.getMethod("read", InputStream.class);
        def secBlobClass = this.class.classLoader.loadClass("com.urbancode.air.securedata.SecureBlob");
        def secConClass = this.class.classLoader.loadClass("com.urbancode.air.securedata.SecretContainer");
        def fromUncMeth = secBlobClass.getMethod("fromEncryptedBytes", secConClass, byte[].class);
        return fromUncMeth.invoke(null, secret, readMeth.invoke(null, is));
    }

    private def newSecretContainer(byte[] sec) {
        def secConImplClass = this.class.classLoader.loadClass("com.urbancode.air.securedata.SecretContainerImpl");
        def con = secConImplClass.getConstructor(byte[].class);
        return con.newInstance([sec] as Object[]);
    }

    private void close(def thingToClose) {
        if (thingToClose != null) {
            try {
                thingToClose.close();
            }
            catch (IOException swallow) {
            }
        }
    }
}
