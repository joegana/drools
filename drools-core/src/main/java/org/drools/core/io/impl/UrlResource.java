/*
 * Copyright 2010 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.core.io.impl;

import java.io.Externalizable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.drools.core.io.internal.InternalResource;
import org.drools.core.util.IoUtils;
import org.drools.core.util.StringUtils;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceType;

/**
 * Borrowed gratuitously from Spring under ASL2.0.
 *
 * Added in local file cache ability for http and https urls.
 *
 * Set the system property: "drools.resource.urlcache" to a directory which can be written to and read from
 * as a cache - so remote resources will be cached with last known good copies.
 */
public class UrlResource extends BaseResource
        implements
        InternalResource,
        Externalizable {

    private static final int    DEFAULT_BUFFER_SIZE      = 1024 * 4;

    private URL                 url;
    private static final String DROOLS_RESOURCE_URLCACHE = "drools.resource.urlcache";
    private String              basicAuthentication      = "disabled";
    private String              username                 = "";
    private String              password                 = "";
    private String              encoding;

    private static final String DROOLS_RESOURCE_URLTIMEOUT = "drools.resource.urltimeout";
    private static final int DEFAULT_TIMEOUT = 10000; // 10 seconds
    private static final int TIMEOUT = initTimeout();

    public UrlResource() {

    }

    public UrlResource(URL url) {
        this.url = getCleanedUrl(url,
                url.toString());
        setSourcePath(this.url.getPath());
        setResourceType(ResourceType.determineResourceType(this.url.getPath()));
    }

    public UrlResource(URL url, String encoding) {
        this(url);
        this.encoding = encoding;
    }

    public UrlResource(String path) {
        try {
            this.url = getCleanedUrl(new URL(path),
                    path);
            setSourcePath(this.url.getPath());
            setResourceType(ResourceType.determineResourceType(this.url.getPath()));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("'" + path + "' path is malformed",
                    e);
        }
    }

    public UrlResource(String path, String encoding) {
        this(path);
        this.encoding = encoding;
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal( out );
        out.writeObject(this.url);
        out.writeObject(this.encoding);
    }

    public void readExternal(ObjectInput in) throws IOException,
            ClassNotFoundException {
        super.readExternal( in );
        this.url = (URL) in.readObject();
        this.encoding = (String) in.readObject();
    }

    public String getEncoding() {
        return this.encoding;
    }

    public String getBasicAuthentication() {
        return basicAuthentication;
    }

    public void setBasicAuthentication(String basicAuthentication) {
        this.basicAuthentication = basicAuthentication;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * This implementation opens an InputStream for the given URL.
     * It sets the "UseCaches" flag to <code>false</code>,
     * mainly to avoid jar file locking on Windows.
     * @see java.net.URL#openConnection()
     * @see java.net.URLConnection#setUseCaches(boolean)
     * @see java.net.URLConnection#getInputStream()
     */
    public InputStream getInputStream() throws IOException {
        URLConnection con = openURLConnection(this.url);
        con.setUseCaches(false);

        if (con instanceof HttpURLConnection) {
            if ("enabled".equalsIgnoreCase(basicAuthentication)) {
                String userpassword = username + ":" + password;
                byte[] authEncBytes = Base64.encodeBase64( userpassword.getBytes(IoUtils.UTF8_CHARSET) );

                ((HttpURLConnection) con).setRequestProperty("Authorization",
                        "Basic " + new String(authEncBytes, IoUtils.UTF8_CHARSET));
            }

        }

        return con.getInputStream();
    }

    private URLConnection openURLConnection(URL url) throws IOException {
        URLConnection con = url.openConnection();
        con.setConnectTimeout(TIMEOUT);
        con.setReadTimeout(TIMEOUT);
        return con;
    }

    private InputStream grabStream() throws IOException {
        URLConnection con = openURLConnection(this.url);
        con.setUseCaches(false);

        if (con instanceof HttpURLConnection) {
            if ("enabled".equalsIgnoreCase(basicAuthentication)) {
                String userpassword = username + ":" + password;
                byte[] authEncBytes = Base64.encodeBase64( userpassword.getBytes(IoUtils.UTF8_CHARSET) );

                ((HttpURLConnection) con).setRequestProperty("Authorization",
                        "Basic " + new String(authEncBytes, IoUtils.UTF8_CHARSET));
            }

        }

        return con.getInputStream();
    }

    public Reader getReader() throws IOException {
        if (this.encoding != null) {
            return new InputStreamReader( getInputStream(), this.encoding );
        } else {
            return new InputStreamReader( getInputStream(), IoUtils.UTF8_CHARSET );
        }
    }

    /**
     * Determine a cleaned URL for the given original URL.
     * @param originalUrl the original URL
     * @param originalPath the original URL path
     * @return the cleaned URL
     */
    private URL getCleanedUrl(URL originalUrl,
            String originalPath) {
        try {
            return new URL(StringUtils.cleanPath(originalPath));
        } catch (MalformedURLException ex) {
            // Cleaned URL path cannot be converted to URL
            // -> take original URL.
            return originalUrl;
        }
    }

    public URL getURL() throws IOException {
        return this.url;
    }

    public boolean hasURL() {
        return true;
    }

    public File getFile() throws IOException {
        try {
            return new File(StringUtils.toURI(url.toString()).getSchemeSpecificPart());
        } catch (Exception e) {
            throw new RuntimeException("Unable to get File for url " + this.url, e);
        }
    }

    public boolean isDirectory() {
        try {
            URL url = getURL();

            if ("file".equals(url.getProtocol())) {

                File file = new File(StringUtils.toURI(url.toString()).getSchemeSpecificPart());

                return file.isDirectory();
            }
        } catch (Exception e) {
            // swallow as returned false
        }

        return false;
    }

    public Collection<Resource> listResources() {
        try {
            URL url = getURL();

            if ("file".equals(url.getProtocol())) {
                File dir = getFile();

                List<Resource> resources = new ArrayList<Resource>();

                for (File file : dir.listFiles()) {
                    resources.add(new FileSystemResource(file));
                }

                return resources;
            }
        } catch (Exception e) {
            // swallow as we'll throw an exception anyway
        }
        throw new RuntimeException("This Resource cannot be listed, or is not a directory");
    }

    /**
     * This implementation compares the underlying URL references.
     */
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        return (obj == this || (obj instanceof UrlResource && this.url.equals(((UrlResource) obj).url)));
    }

    /**
     * This implementation returns the hash code of the underlying URL reference.
     */
    public int hashCode() {
        return this.url.hashCode();
    }

    public String toString() {
        return "UrlResource[path=" + this.url.toString() + "]";
    }

    private static File getCacheDir() {
        String root = System.getProperty(DROOLS_RESOURCE_URLCACHE, "NONE");
        if (root.equals("NONE")) {
            return null;
        } else {
            return new File(root);
        }
    }

    private static int initTimeout() {
        try {
            return Integer.parseInt(System.getProperty( DROOLS_RESOURCE_URLTIMEOUT ));
        } catch (Exception e) {
            return DEFAULT_TIMEOUT;
        }
    }
}
