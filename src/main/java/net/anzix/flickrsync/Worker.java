package net.anzix.flickrsync;

import java.io.File;

public interface Worker {

	public abstract void init() throws Exception;

	public abstract String validateSet(String setId) throws Exception;

	public abstract String createSet(String name, String firstPhotoId) throws Exception;

	public abstract void assignPhotoToSet(String setId, String photoId) throws Exception;

	public abstract String uploadFile(File img) throws Exception;

	public void assignSetToCollection(String setId, String setName, String collectionId) throws Exception;

}