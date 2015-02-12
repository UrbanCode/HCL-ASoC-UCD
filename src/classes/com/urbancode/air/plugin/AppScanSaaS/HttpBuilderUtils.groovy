package com.urbancode.air.plugin.AppScanSaaS

import groovyx.net.http.HTTPBuilder

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.apache.http.conn.scheme.Scheme
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.content.InputStreamBody
import org.apache.http.entity.mime.content.StringBody


public class HttpBuilderUtils {
	public static void ignoreSSLErrors(HTTPBuilder httpBuilder) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
		TrustStrategy trustStrat = new TrustStrategy(){
					public boolean isTrusted(X509Certificate[] chain, String authtype)
					throws CertificateException {
						return true;
					}
				};

		SSLSocketFactory sslSocketFactory = new SSLSocketFactory(trustStrat, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

		httpBuilder.getClient().getConnectionManager().getSchemeRegistry().register(new Scheme("https",443, sslSocketFactory ) );
	}
}
