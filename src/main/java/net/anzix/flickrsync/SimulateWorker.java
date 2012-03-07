package net.anzix.flickrsync;

import java.io.File;

public class SimulateWorker implements Worker {

	@Override
	public void init() throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public String validateSet(String setId) throws Exception {
		return setId;
	}

	@Override
	public String createSet(String name, String firstPhotoId) throws Exception {
		System.out.println("Creating set " + name);
		return "" + Math.random();
	}

	@Override
	public void assignPhotoToSet(String setId, String photoId) throws Exception {
		System.out.println("Assigning " + photoId + " to set " + setId);

	}

	@Override
	public String uploadFile(File img) throws Exception {
		System.out.println("Uploading file " + img.getAbsolutePath());
		return "" + Math.random();
	}

	@Override
	public void assignSetToCollection(String setId, String setName, String collectionId) throws Exception {
		System.out.println("Assign set " + setId + " to collection " + collectionId);

	}

}
