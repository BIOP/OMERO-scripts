#@String (visibility=MESSAGE, value="OMERO connection", required=false) msg0
#@String(label="Server name", value="omero-server.epfl.ch", persist=true) host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String (visibility=MESSAGE, value="Setup object to process", required=false) msg1
#@String(label="Object to process", choices={"image","dataset","project"}) object_type
#@String(label="Object ID or object(s) URL", value=119273) ids
#@String (visibility=MESSAGE, value="Projection parameters", required=false) msg2
#@Boolean(label="Full stack projection", value=true) fullStackProjection
#@Integer(label="Start Z", value=-1) startZ
#@Integer(label="End Z", value=-1) endZ
#@String(label="Projection type", choices={"max","min"}, value="max") projectionType
#@File(label="Temporary saving folder", required=false) tmpFolder
#@String (visibility=MESSAGE, value="Output", required=false) msg4
#@Boolean (label="Send projection", value=true) isSendBackToOmero
#@Boolean (label="Show image", value=true) showImages


/* 
 * This script performes a Z-projection over a stack, stored on OMERO.
 * The projected image is saved locally as pyrmadial ome.tiff and
 * finally sent back to OMERO, with tags and key-value pairs.
 * The projection type can be chosen by the user among the available options, 
 * as well as the starting /ending z position.
 * 
 * 
 * Dependencies
 *  - Fiji update site PTBIOP, with simple-omero-client 5.19.0
 *  - Official OMERO plugin omero_ij-5.8.6.jar or later
 * 
 * Author: Rémy Dornier, PTBIOP 
 * date: 2026-06.17
 * version: 1.0.0
 * 
 * -----------------------------------------------------------------------------
 * Copyright (c) 2026 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
 * All rights reserved.
 * 
 * Licensed under the BSD-3-Clause License:
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided 
 * that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer 
 *    in the documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products 
 *     derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, 
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * -----------------------------------------------------------------------------
 * 
 */


// global variables
NAMESPACE = "z_projection"


// Connection to server
port = 4064
Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "Connected to "+host
	
	try{
		def idList = []
		try{
			Long.parseLong(ids)
			idList.add(id)
		}catch (Exception e){
			idList = parseURL(ids)
		}
		
		idList.each{id ->
			switch (object_type){
				case "image":	
					processImage(user_client, user_client.getImage(id), null)
					break	
				case "dataset":
					processDataset(user_client, user_client.getDataset(id))
					break
				case "project":
					processProject(user_client, user_client.getProject(id))
					break
			}
		}
	} finally{
		user_client.disconnect()
		println "Disconnected from "+host
	}
} else {
	println "Not able to connect to "+host
}

println "End of the script"
return



/**
 * apply processing on OMERO image ; remove and send new objects
 * 
 */
def processImage(user_client, image_wpr, dataset_wpr){
	// clear Fiji env
	IJ.run("Close All", "");
	
	// open the image on Fiji
	println "Working on image '"+image_wpr.getName()+"': "+image_wpr.getId()
	ImagePlus imp = image_wpr.toImagePlus(user_client);
	if(showImages) imp.show()

	println "Intensity projection : Start"
	
	// check start and end 
	if(startZ <= 0 || startZ > imp.getNSlices()){
		startZ = 1	
	}
	if(endZ <= 0 || endZ > imp.getNSlices()){
		endZ = imp.getNSlices()
	}
	
    if(startZ > endZ){
    	endTmp = endZ
    	endZ = startZ
    	startZ = endTmp
    }
	
	def projectionImp = ZProjector.run(imp, projectionType+" all", startZ, endZ);
	projectionImp.show() // necessary for Kheops working
	
	println "Intensity projection : End"

	if (isSendBackToOmero){
		
		// get the dataset to import image into
		if(dataset_wpr == null){
			dataset_wpr = getDataset(user_client, image_wpr)
		}
		def datasetId = dataset_wpr.getId()
		
		// check results folder exists
		def imgPath = ""
		if(tmpFolder == null || !tmpFolder.exists()){
			imgPath = System.getProperty("user.home") + File.separator + "Downloads"	
		}else{
			imgPath = tmpFolder.getAbsolutePath()
		}
		
		def imgName = image_wpr.getName().split("\\.")[0]
		projectionImp.setTitle(imgName+"_"+projectionType+"_proj")
		
		// saving the image locally
		println "Saving projection locally..."
		IJ.run("Kheops - Convert Image to Pyramidal OME TIFF", "output_dir=["+imgPath+"] compression=LZW subset_channels= subset_slices= subset_frames= compress_temp_files=false");
		imgPath = imgPath + File.separator + projectionImp.getTitle() + ".ome.tiff"
		
		def projImage_wpr = null
		try{
			// sending projection to Omero
			println "Sending projection to OMERO..."
			def imgID = user_client.getDataset(datasetId).importImage(user_client, imgPath)
			projImage_wpr = user_client.getImage(imgID)
		}catch(Exception e){
			println "ERROR: The projection cannot be sent to OMERO"
			println e.toString() + "\n" + getErrorStackTraceAsString(e)
		}finally{
			def tmpFile = new File(imgPath)
			if(tmpFile.exists()){
				// remove the tmp file
			    println "Deleting projection locally..."
			    tmpFile.delete()
			}
		}

		if(projImage_wpr != null){
			def classTag = projectionType + "_projection"
			println "Sending tags and KVPs to projection image..."
			saveTagsOnOmero(user_client, projImage_wpr, Collections.singletonList(classTag))
			
			// send kvp to Omero
			def analysisKvpMap = new LinkedHashMap<>()
			analysisKvpMap.put("Source image ID", String.valueOf(image_wpr.getId()))
			analysisKvpMap.put("Source image", image_wpr.getName())
			analysisKvpMap.put("Projection type", projectionType + "_projection")
			analysisKvpMap.put("Z-slices", String.valueOf(startZ) + "-" + String.valueOf(endZ))
			addKeyValueToOMERO(user_client, projImage_wpr, analysisKvpMap, NAMESPACE)
		}
	}
}


def getDataset(user_client, image_wpr){
	// Get dataset information from the image ID
	List<DatasetWrapper> dataset_wpr_list = image_wpr.getDatasets(user_client)
	
	if(!dataset_wpr_list.isEmpty()){
		return dataset_wpr_list.get(0)
	}
	else{
		println "WARNING : Your image is part of a plate/screen, not part of a dataset/project"
		println "A new dataset is created to stored projection images"
		return createOmeroDataset(user_client, null, projectionType +"_projections")
	}

}

	
/**
 * return the dataset wrapper corresonding a new dataset or to an existing dataset, depending on the user choice
 */
def createOmeroDataset(user_client, projectWrapper, datasetName){	
	// create a new dataset
	def dataset = new DatasetData();
	dataset.setName(datasetName);
    
    // send the dataset on OMERO
    dataset = user_client.getDm().createDataset(user_client.getCtx(), dataset, projectWrapper == null ? null : projectWrapper.asProjectData())
    def newId = dataset.getId()
    
    // get the corresponding wrapper
    def datasetWrapper = user_client.getDataset(newId)
	
	return datasetWrapper
}



/**
 * Parse OMERO URL to get the list of ids
 */
def parseURL(url){
	def idList = []
	
	// Check that URL is correct
	if (url.contains("?show=")) {
	    def showPart = url.split("\\?show=")[1]
	    
	    // get everything after the |
	    def items = showPart.split("\\|")
	    
	    def results = []
	    
	    // Parse each element
	    items.each { item ->
	        def matcher = (item =~ /^([a-zA-Z]+)-(\d+)$/)
	        if (matcher.matches()) {
	            results << [
	                type: matcher.group(1),
	                id: matcher.group(2).toInteger()
	            ]
	        }
	    }
		
		// get the ids
	    def type = results.collect { it.type }.unique()
	    if(type.size() == 1 && type.get(0).equalsIgnoreCase(object_type)){
	    	idList = results.collect { it.id }
	    } else {
	    	 println "The type of objects in the URL "+type+" does not match with the selected object type "+object_type
	    }
	
	} else {
	    println "The URL doesn't contain '?show='; it's not coming from OMERO."
	}
	return idList
}




/**
 * Add a list of tags to the specified object
 * 
 */
def saveTagsOnOmero(user_client, annotatableWrapper, tags){
	def tagsToAdd = []
	
	// get existing tags
	def groupTags = user_client.getTags()
	def imageTags = annotatableWrapper.getTags(user_client)
	
	// find if the tag to add already exists on OMERO. If yes, they are not added twice
	tags.each{tag->
		if(tagsToAdd.find{ it.getName().toLowerCase().equals(tag.toLowerCase()) } == null){
			// find if the requested tag already exists
			new_tag = groupTags.find{ it.getName().toLowerCase().equals(tag.toLowerCase()) } ?: new TagAnnotationWrapper(new TagAnnotationData(tag))
			
			// add the tag if it is not already the case
			imageTags.find{ it.getName().toLowerCase().equals(new_tag.getName().toLowerCase()) } ?: tagsToAdd.add(new_tag)
		}
	}
	annotatableWrapper.link(user_client, (TagAnnotationWrapper[])tagsToAdd.toArray())
}



/**
 * Add the key value to OMERO attach to the current repository wrapper
 * 
 * */
def addKeyValueToOMERO(user_client, annotatableWrapper, kvpMap, NAMESPACE){
	def kvpList = []
	
	// get the list of entries
	def listOfEntry = new ArrayList<>(kvpMap.entrySet())
	
	// convert to MapAnnotation
	MapAnnotationWrapper mapAnnWrapper = new MapAnnotationWrapper((Collection<? extends Entry<String, String>>)listOfEntry)
	mapAnnWrapper.setNameSpace(NAMESPACE)
	
	// link
	kvpList.add(mapAnnWrapper)
	annotatableWrapper.link(user_client, (MapAnnotationWrapper[])kvpList.toArray())
}



/**
 * process all images within a dataset
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		dataset_wpr : OMERO dataset
 * 
 * */
def processDataset( user_client, dataset_wpr ){
	dataset_wpr.getImages(user_client).each{ image_wpr ->
		processImage(user_client , image_wpr, dataset_wpr)
	}
}


/**
 * process all datasets within a project
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		project_wpr : OMERO project
 * 
 * */
def processProject( user_client, project_wpr ){
	project_wpr.getDatasets().each{ dataset_wpr ->
		processDataset(user_client , dataset_wpr)
	}
}


def getErrorStackTraceAsString(Exception e){
    return Arrays.stream(e.getStackTrace()).map(StackTraceElement::toString).reduce("",(a, b)->a + "     at "+b+"\n");
}


/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.roi.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import omero.gateway.model.TagAnnotationData;
import ij.IJ
import omero.gateway.model.DatasetData
import ij.plugin.ZProjector
import ij.Prefs
import ij.ImagePlus
import omero.gateway.model.ROIData
import java.awt.Color
import java.util.stream.Collectors
import ij.plugin.ImageCalculator
import java.util.Map.Entry
import javax.swing.JOptionPane; 
import omero.model.FileAnnotationI;