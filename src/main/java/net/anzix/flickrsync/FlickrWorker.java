package net.anzix.flickrsync;

import com.aetrion.flickr.*;
import com.aetrion.flickr.auth.Auth;
import com.aetrion.flickr.auth.AuthInterface;
import com.aetrion.flickr.auth.AuthUtilities;
import com.aetrion.flickr.auth.Permission;
import com.aetrion.flickr.uploader.UploadMetaData;
import com.aetrion.flickr.uploader.Uploader;
import com.aetrion.flickr.util.StringUtilities;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.util.*;

public class FlickrWorker implements Worker {
    private Flickr f;
    private Map<String, Map<String, String>> cache = new HashMap<String, Map<String, String>>();
    private String token;
    private boolean verbose = false;
    private Map<String, String> setToCollection;

    public FlickrWorker(String token, boolean verbose) {
        this.token = token;
        this.verbose = verbose;
    }

    /*
      * (non-Javadoc)
      *
      * @see net.anzix.flickrsync.Worker#init()
      */
    @Override
    public void init() throws Exception {
        f = new Flickr("35c1f76f44831da16371329162eae7b4", "70a10e2cd57e534f", new REST());

        Flickr.debugStream = false;
        Flickr.debugRequest = false;
        RequestContext requestContext = RequestContext.getRequestContext();
        AuthInterface authInterface = f.getAuthInterface();
        String frob = authInterface.getFrob();
        Auth auth = null;
        if (token == null) {
            URL url = f.getAuthInterface().buildAuthenticationUrl(Permission.WRITE, frob);
            System.out.println(url.toString());
            Thread.sleep(10000);

            auth = authInterface.getToken(frob);
            token = auth.getToken();

        } else {
            auth = new Auth();
            auth.setToken(token);
        }
        auth = authInterface.checkToken(token);
        requestContext.setAuth(auth);
    }

    /*
      * (non-Javadoc)
      *
      * @see net.anzix.flickrsync.Worker#validateSet(java.lang.String)
      */
    @Override
    public String validateSet(String setId) throws Exception {
        try {
            return f.getPhotosetsInterface().getInfo(setId).getId();
        } catch (FlickrException e) {
            if (e.getErrorCode().equals("1")) {
                System.out.println("ERROR set is not exists");
                return null;
            } else {
                throw e;
            }
        }
    }

    /*
      * (non-Javadoc)
      *
      * @see net.anzix.flickrsync.Worker#createSet(java.lang.String, java.lang.String)
      */
    @Override
    public String createSet(String name, String firstPhotoId) throws Exception {
        if (verbose) {
            System.out.println("Creating set " + name);
        }
        return f.getPhotosetsInterface().create(name, "", firstPhotoId).getId();
    }

    /*
      * (non-Javadoc)
      *
      * @see net.anzix.flickrsync.Worker#assignPhotoToSet(java.lang.String,
      * java.lang.String)
      */
    @Override
    public void assignPhotoToSet(String setId, String photoId) throws Exception {
        if (verbose) {
            System.out.println("Assigning photo " + photoId + " to set " + setId);
        }
        if (setToCollection == null) {

        }
        f.getPhotosetsInterface().addPhoto(setId, photoId);
    }

    /*
      * (non-Javadoc)
      *
      * @see net.anzix.flickrsync.Worker#uploadFile(java.io.File)
      */
    @Override
    public String uploadFile(File img) throws Exception {
        if (verbose) {
            System.out.println("Uploading file " + img.getAbsolutePath());
        }
        Uploader uploader = f.getUploader();
        UploadMetaData umd = new UploadMetaData();
        umd.setPublicFlag(false);
        umd.setFriendFlag(false);
        umd.setFamilyFlag(true);
        String name = img.getName();
        name = name.substring(0, name.lastIndexOf('.'));
        umd.setTitle(name);
        return uploader.upload(new FileInputStream(img), umd);

    }

    @Override
    public void assignSetToCollection(String setId, String setName, String collectionId) throws Exception {
        if (setToCollection == null) {
            getSets(collectionId);
        }
        if (setToCollection.get(setId) != null && setToCollection.get(setId).equals(collectionId)) {
            if (verbose) {
                System.out.println("Set " + setId + " is already part of the collection " + collectionId);
                return;
            }
        }
        if (setId == null) {
            throw new IllegalArgumentException("setId is null");
        }
        Map<String, String> st = getSets(collectionId);
        if (verbose) {
            System.out.println("Assigning set " + setId + " to " + collectionId);
        }


        st.put(setName, setId);
        cache.put(collectionId, st);

        List<Parameter> parameters = new ArrayList<Parameter>();

        parameters.add(new Parameter("method", "flickr.collections.editSets"));
        parameters.add(new Parameter("api_key", f.getApiKey()));

        parameters.add(new Parameter("collection_id", collectionId));
        parameters
                .add(new Parameter("photoset_ids", StringUtilities.join(new ArrayList<String>(st.values()), ",", false)));
        parameters.add(new Parameter("api_sig", AuthUtilities.getSignature(f.getSharedSecret(), parameters)));
        f.getTransport().post(f.getTransport().getPath(), parameters);

    }

    private Map<String, String> getSets(String collectionId) throws Exception {
        if (setToCollection == null) {
            setToCollection = new HashMap<String, String>();
        }
        if (cache.get(collectionId) != null) {
            return cache.get(collectionId);
        }
        if (verbose) {
            System.out.println("Getting collection contents " + collectionId);
        }
        List<Parameter> parameters = new ArrayList<Parameter>();
        Map<String, String> st = new TreeMap<String, String>();
        parameters.add(new Parameter("method", "flickr.collections.getTree"));
        parameters.add(new Parameter("api_key", f.getApiKey()));
        parameters.add(new Parameter("collection_id", collectionId));
        parameters.add(new Parameter("api_sig", AuthUtilities.getSignature(f.getSharedSecret(), parameters)));
        Response post = f.getTransport().post(f.getTransport().getPath(), parameters);
        Element payload = post.getPayload();
        for (int i = 0; i < payload.getChildNodes().getLength(); i++) {
            Node n = payload.getChildNodes().item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                Element e = (Element) n;
                String nid = e.getAttribute("id");
                NodeList nl = e.getElementsByTagName("set");
                for (int j = 0; j < nl.getLength(); j++) {
                    Element a = (Element) nl.item(j);
                    st.put(a.getAttribute("title"), a.getAttribute("id"));
                    setToCollection.put(a.getAttribute("id"), nid);
                }
            }
        }
        cache.put(collectionId, st);
        return cache.get(collectionId);
    }
}
