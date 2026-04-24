#@String(label="Host", value="omero-server.epfl.ch") host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String(label="Object to process", choices={"image","dataset","project","well","plate","screen"}) object_type
#@Long(label="Object ID", value=119273) id
#@String(label="New Tag(s)", value = "new_tag1,new_tag2") USER_TAGS


/* Code description 
 *
 * Adds new tags to the select object.
 * Tags have to be comma-separated
 * 
 * 
 * Dependencies
 *  - Fiji update site OMERO 5.5-5.6
 *  - Fiji update site PTBIOP, with simple-omero-client
 * 
 * Author: Rémy Dornier, EPFL - PTBIOP 
 * Date: 2022.05.18
 * Version: 1.0.4
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
 * - 2023-06-15 : Add multiple tags at the same time + remove unnecessary imports.
 * - 2023-10-17 : Add popup message at the end of the script and if an error occurs while running
 * - 2023.11.06 : Remove popup messages from template
 * - 2026.04.23 : Update add tag method + run support -v1.0.4
 * 
 */


/**
 * Main. Connect to OMERO, process tags and disconnect from OMERO
 * 
 */
 
// Connection to server
port = 4064
Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "Connected to "+host
	
	try{
		switch (object_type){
			case "image":	
				processTag(user_client, user_client.getImage(id))
				break	
			case "dataset":
				processTag(user_client, user_client.getDataset(id))
				break
			case "project":
				processTag(user_client, user_client.getProject(id))
				break
			case "well":
				processTag(user_client, user_client.getWells(id))
				break
			case "plate":
				processTag(user_client, user_client.getPlates(id))
				break
			case "screen":
				processTag(user_client, user_client.getScreens(id))
				break
		}
		
		println "Tags '"+tags+"' have been successfully added on "+object_type+ " "+id
	
	}finally{
		user_client.disconnect()
		println "Disconnected from "+host
	}	
}else{
	println "Not able to connect to "+host
}
return


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


/**
 * Add a new tags to the object.
 * 
 * inputs
 * 		user_client : OMERO client
 * 		wpr : OMERO object wrapper (image, dataset, project, well, plate, screen)
 * 
 * */
def processTag(user_client, repository_wpr){
	def userTags = USER_TAGS.split(",")
	saveTagsOnOmero(user_client, repository_wpr, userTags)
}


/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import omero.gateway.model.TagAnnotationData;