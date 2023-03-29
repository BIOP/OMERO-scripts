#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@Long(label="Dataset ID", value=119273) id


/* = CODE DESCRIPTION =
 * This is a template to interact with OMERO. 
 * User can specify the ID of a dataset
 * Macro, Overview and label .vsi images within the dataset will be renamed like "_Overview_imageName.vsi"
 * 
 * == INPUTS ==
 *  - credentials 
 *  - id
 * 
 * == OUTPUTS ==
 *  - Image renaming and new tags on OMERO.
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.9.2 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written Rémy Dornier, EPFL - SV -PTECH - BIOP 
 * 20.03.2023
 * 
 * = COPYRIGHT =
 * © All rights reserved. ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP), 2022
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
 * 
 * Last modifed : 2023-03-27
 * 
 * History : 
 * 	- 2023-03-27 : add "_" before the name
 * 
 */

/**
 * Main. Connect to OMERO, process images and disconnect from OMERO
 * 
 */

// Connection to server
host = "omero-server.epfl.ch"
port = 4064

Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())


if (user_client.isConnected()){
	println "\nConnected to "+host
	
	try{	
		// get the dataset
		def datasetWrapper = user_client.getDataset(id);
		
		// list images within the dataset
		datasetWrapper.getImages(user_client).each{ img_wpr ->
			processImage(user_client , img_wpr)	
		}
		
	} finally{
		user_client.disconnect()
		println "Disonnected "+host
	}
	
	println "Process : DONE !"
	return
	
}else{
	println "Not able to connect to "+host
}


/**
 * Rename .vsi images in order to group label, macro and overview images together.
 * Add the corresponding tag to each image.
 * 
 * inputs
 * 		user_client : OMERO client
 * 		image_wpr : OMERO image
 * 
 * */
def processImage(user_client, img_wpr){
	def name = img_wpr.getName()
	def newName = name
	def tag = null
	def existingTags = user_client.getTags();
	def updateImage = true
	
	if(name.contains(".vsi")){
		// find label, macro or overview text and create the corresponding tag
		if(name.contains("label")){
			newName = "Label_" + name.substring(0, name.indexOf(".vsi") + 4)
			tag = existingTags.find{ it.getName().equals("Label") } ?: new TagAnnotationWrapper(new TagAnnotationData("Label"))
		}
		else if(name.contains("macro")){
			newName = "Macro image_"+name.substring(0,name.indexOf(".vsi") + 4)
			tag = existingTags.find{ it.getName().equals("Macro image") } ?: new TagAnnotationWrapper(new TagAnnotationData("Macro image"))
		}
		else if(name.contains("overview")){
			newName = "Overview_"+name.substring(0,name.indexOf(".vsi") + 4)
			tag = existingTags.find{ it.getName().equals("Overview") } ?: new TagAnnotationWrapper(new TagAnnotationData("Overview"))
		}
		else if(!name.startsWith("_"))
			newName = "_" + name
		else
			updateImage = false
		
		if(updateImage){
			// update image name
			img_wpr.setName(newName)
			img_wpr.saveAndUpdate(user_client)
			
			if(tag != null){			
				// add corresponding tag
				img_wpr.getTags(user_client).find{ it.getName().equals(tag.getName()) } ?: img_wpr.addTag(user_client, tag)
			}
		}
	}	
}


/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*

import omero.gateway.model.*
