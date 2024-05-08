#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String(label="Object to process", choices={"image","dataset","project","well","plate","screen"}) object_type
#@Long(label="Object ID", value=119273) id
#@String(label="New Tag(s)", value = "new_tag1,new_tag2") tags


/* = CODE DESCRIPTION =
 * This is a template to interact with OMERO . 
 * User can specify the image to be imported (must be stored in a local environnement) and the ID of the dataset where to import the image.
 * 
 * == INPUTS ==
 *  - credentials 
 *  - id
 *  - object type
 *  - tag
 * 
 * == OUTPUTS ==
 * - Add a tag on object to OMERO
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.9.1 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV -PTECH - BIOP 
 * 2022-05-18
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
 * == HISTORY ==
 * - 2023-06-15 : Add multiple tags at the same time + remove unnecessary imports.
 * - 2023-10-17 : Add popup message at the end of the script and if an error occurs while running
 * - 2023.11.06 : Remove popup messages from template
 * 
 */


/**
 * Main. Connect to OMERO, process tags and disconnect from OMERO
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


/**
 * Add a new tags to the object.
 * 
 * inputs
 * 		user_client : OMERO client
 * 		wpr : OMERO object wrapper (image, dataset, project, well, plate, screen)
 * 
 * */
def processTag(user_client, wpr){
	
	def tagsToAdd = []
	
	// get existing tags
	def groupTags = user_client.getTags()
	def imageTags = wpr.getTags(user_client)
	
	// find if the tag to add already exists on OMERO. If yes, they are not added twice
	tags.split(",").each{tag->
		// find if the requested tag already exists
		new_tag = groupTags.find{ it.getName().equals(tag) } ?: new TagAnnotationWrapper(new TagAnnotationData(tag))
		
		// add the tag if it is not already the case
		imageTags.find{ it.getName().equals(new_tag.getName()) } ? println("Tag "+tag+" already attached to the "+object_type) : tagsToAdd.add(new_tag)
	}
	
	println("Adding tags :")
	tagsToAdd.each{println(it.getName())}
	
	// add all tags to the image
	wpr.addTags(user_client, (TagAnnotationWrapper[])tagsToAdd.toArray())	
}


/**
 * Return a formatted string of the exception
 */
def getErrorStackTraceAsString(Exception e){
    return Arrays.stream(e.getStackTrace()).map(StackTraceElement::toString).reduce("",(a, b)->a + "     at "+b+"\n");
}



/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import omero.gateway.model.TagAnnotationData;