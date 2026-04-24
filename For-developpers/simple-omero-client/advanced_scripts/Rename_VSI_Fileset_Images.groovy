#@String(label="Host", value="omero-server.epfl.ch") host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@Long(label="Dataset ID", value=119273) id


/* Code description
 *  
 * User can specify the ID of a dataset
 * Macro, Overview and label .vsi images within the dataset will be renamed like "_Overview_imageName.vsi"
 * 
 * 
 * Dependencies
 *  - Fiji update site OMERO 5.5-5.6
 *  - Fiji update site PTBIOP, with simple-omero-client
 * 
 * Author: Rémy Dornier, EPFL - PTBIOP 
 * Date: 2023.03.20
 * Version: 1.1.0
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
 * History
 * 	- 2023.03.27 : add "_" before the name
 * 	- 2026.04.23 : Update UI and properly link tags -v1.1.0
 * 
 */

/**
 * Main. Connect to OMERO, process images and disconnect from OMERO
 * 
 */

// Connection to server
port = 4064
Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())


if (user_client.isConnected()){
	println "Connected to "+host
	
	try{	
		// get the dataset
		def datasetWrapper = user_client.getDataset(id);
		
		// list images within the dataset
		datasetWrapper.getImages(user_client).each{ image_wpr ->
			processImage(user_client , image_wpr)	
		}
		
		println "Process : DONE !"

	} finally{
		user_client.disconnect()
		println "Disconnected "+host
	}
}else{
	println "Not able to connect to "+host
}
return


/**
 * Rename .vsi images in order to group label, macro and overview images together.
 * Add the corresponding tag to each image.
 * 
 * inputs
 * 		user_client : OMERO client
 * 		image_wpr : OMERO image
 * 
 * */
def processImage(user_client, image_wpr){
	def name = image_wpr.getName()
	def newName = name
	def tag = []
	def updateImage = true
	
	if(name.contains(".vsi")){
		// find label, macro or overview text and create the corresponding tag
		if(name.contains("label")){
			newName = "Label_" + name.substring(0, name.indexOf(".vsi") + 4)
			tag.add("Label")
		}else if(name.contains("macro")){
			newName = "Macro image_"+name.substring(0,name.indexOf(".vsi") + 4)
			tag.add("Macro image")
		}else if(name.contains("overview")){
			newName = "Overview_"+name.substring(0,name.indexOf(".vsi") + 4)
			tag.add("Overview")
		}else if(!name.startsWith("_")){
			newName = "_" + name
		} else{
			updateImage = false
		}
		
		if(updateImage){
			// update image name
			image_wpr.setName(newName)
			image_wpr.saveAndUpdate(user_client)
			
			if(!tag.isEmpty()){			
				// add corresponding tag
				saveTagsOnOmero(user_client, image_wpr, tag)
			}
		}
	}
}



/**
 * Add a list of tags to the specified image
 * 
 */
def saveTagsOnOmero(user_client, imageWrapper, tags){
	def tagsToAdd = []
	
	// get existing tags
	def groupTags = user_client.getTags()
	def imageTags = imageWrapper.getTags(user_client)
	
	// find if the tag to add already exists on OMERO. If yes, they are not added twice
	tags.each{tag->
		if(tagsToAdd.find{ it.getName().toLowerCase().equals(tag.toLowerCase()) } == null){
			// find if the requested tag already exists
			new_tag = groupTags.find{ it.getName().toLowerCase().equals(tag.toLowerCase()) } ?: new TagAnnotationWrapper(new TagAnnotationData(tag))
			
			// add the tag if it is not already the case
			imageTags.find{ it.getName().toLowerCase().equals(new_tag.getName().toLowerCase()) } ?: tagsToAdd.add(new_tag)
		}
	}
	imageWrapper.addTags(user_client, (TagAnnotationWrapper[])tagsToAdd.toArray())
}


/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import omero.gateway.model.*