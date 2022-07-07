#@String(label="Host", value="omero-server.epfl.ch") host
#@Integer(label="Port", value = 4064) port
#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@Long(label="ID", value=119273) id
#@String(label="Object", choices={"image","dataset","project","well","plate","screen"}) object_type
//#@Boolean(label="Process all images in the current dataset", value = false, persist=false) processAllImages
//#@File(label="Tags as csv file", value="Choose a csv") csvTag
#@String(label="Action", choices={"1--add","2--get","3--delete one tag","4--delete all tags"}) action



/* = CODE DESCRIPTION =
 * This is a template to interact with OMERO . 
 * User can specify the image to be imported (must be stored in a local environnement) and the ID of the dataset where to import the image.
 * 
 * == INPUTS ==
 *  - host
 *   -port
 *  - credentials 
 *  - id
 *  - object type
 *  - boolean to process all images in the current dataset
 *  - csv file to read
 *  - action to perform
 * 
 * == OUTPUTS ==
 *  - if adding a tag, it will upolad to OMERO every tags contained in the csv file and link them to the object specified by its id
 *  - if getting a tag, it will list all the tags linked to the current user AND ALL available tags in its group
 *  - if deleting a tag, it will delete ALL the tags linked to the object specified by its id
 * 
 * = DEPENDENCIES =
 *  - omero_ij : https://github.com/ome/omero-insight/releases/download/v5.7.0/omero_ij-5.7.0-all.jar
 *  - simple-omero-client : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV -PTECH - BIOP 
 * 18.05.2022
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
 */

// Only works with project/dataset/image => not with screen/plate/well

/**
 * Main. Connect to OMERO, process tags and disconnect from OMERO
 * 
 */
 
// Connection to server
Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "Connected to "+host +"\n"
	
	try{
		
		switch (object_type){
		case "image":	
			/*if (processAllImages){
				List<DatasetWrapper> dsList = user_client.getImage(id).getDatasets(user_client)
				for(ImageWrapper image : dsList.get(0).getImages(user_client)){
					processTag( user_client, user_client.getImage(image.getId()) )
					println "processed image : "+image.getId()
				}
			}
			else*/
				processTag( user_client, user_client.getImage(id) )
			break	
		case "dataset":
			processTag( user_client, user_client.getDataset(id) )
			break
		case "project":
			processTag( user_client, user_client.getProject(id) )
			break
		case "well":
			processTag( user_client, user_client.getWells(id) )
			break
		case "plate":
			processTag( user_client, user_client.getPlates(id))
			break
		case "screen":
			processTag( user_client, user_client.getScreens(id))
			break
		}
		
	} finally{
		user_client.disconnect()
		println "\n Disonnected "+host
	}
	
	println "Processing of tags for "+object_type+ " "+id+" : DONE !"
	return
	
}else{
	println "Not able to connect to "+host
}

/**
 * Select the right method according to user choice
 * 
 * inputs
 * 		user_client : OMERO client
 * 		wpr : OMERO object wrapper (image, dataset, project, well, plate, screen)
 * 
 * */
def processTag(user_client, wpr){
	
	switch (action){
		case "1--add":
			addNewTags( user_client, wpr)
			break
		case "2--get":
			println "\n User's tags: \n"
			getTags( user_client, wpr )
			println "\n Available tags for the user: \n"
			getUserTags(user_client)
			break
		case "3--delete one tag":
			deleteTagOnImage( user_client, wpr)
			break
		case "4--delete all tags":
			deleteAllTagsOnImage( user_client, wpr)
			break
	}	
}


/**
 * Print all tags belogning to the current OMERO object
 * 
 * inputs
 * 		user_client : OMERO client
 * 		wpr : OMERO object wrapper (image, dataset, project, well, plate, screen)
 * 
 * */
def getTags(user_client, wpr){
		List<TagAnnotationWrapper> annotMaps = wpr.getTags(user_client)
		annotMaps.each{println "Tags name : " +it.getName()+" (id : "+it.getId()+")"}
}


/**
 * Print all tags belogning to the current group
 * 
 * inputs
 * 		user_client : OMERO client
 * 		wpr : OMERO object wrapper (image, dataset, project, well, plate, screen)
 * 
 * */
def getUserTags(user_client){
		List<TagAnnotationWrapper> annotMaps = user_client.getTags()
		annotMaps.sort{it.getName()}
		annotMaps.each{println "Tags name : " +it.getName()+" (id : "+it.getId()+")"}
}


/**
 * Add a new tags to the object. Tags can be added manually (one by one) or automatically (in batch)
 * using a csv file formatted as : tag\n
 * 
 * inputs
 * 		user_client : OMERO client
 * 		wpr : OMERO object wrapper (image, dataset, project, well, plate, screen)
 * 
 * */
def addNewTags(user_client, wpr){
	gd =  new GenericDialog("Choose how to add tags")
	String[] choices = ["Manual", "Automatic"]
	gd.addChoice("Selection", choices, "Manual") 
	gd.addMessage("If manual") 
	gd.addStringField("tag name", "tag", 50)
	gd.addMessage("If automatic") 
	gd.addFileField("csv file path", "new path")
	gd.showDialog();
	
	if(gd.wasCanceled())
		return -1
	
	def choice = gd.getNextChoice()
	def tagName = gd.getNextString()
	def csvFilePath = gd.getNextString()
	
	println tagName
	println choice
	println csvFilePath
	
	if(choice == "Manual"){
		raw_tag = user_client.getTags().find{ it.getName().equals(tagName) } ?: new TagAnnotationWrapper(new TagAnnotationData(tagName))
		wpr.getTags(user_client).find{ it.getName().equals(raw_tag.getName()) } ?: wpr.addTag(user_client, raw_tag)
	}
	else{		
		//read tags
		tagsList = readTagCSV(csvFilePath)
		
		// add the new tags
		tagsList.each{newtag ->
			raw_tag = user_client.getTags().find{ it.getName().equals(newtag) } ?: new TagAnnotationWrapper(new TagAnnotationData(tagName))
			wpr.getTags(user_client).find{ it.getName().equals(raw_tag.getName()) } ?: wpr.addTag(user_client , raw_tag)
		}
	}
}


def deleteTags(user_client, wpr){

}

def deleteAllTagsOnImage(user_client, wpr){
	//wpr.getTags(user_client).each{wpr.delete(user_client, it)}
}



def deleteTagOnImage(user_client, wpr){
	
	/*gd =  new GenericDialog("Delete tag")
	gd.addStringField("tag name", "tag", 50)
	gd.showDialog();
	
	if(gd.wasCanceled())
		return -1
	
	def tagName = gd.getNextString()
	
	tag2delete = wpr.getTags(user_client).find{ it.getName().equals(tagName) } ?: null
	println tag2delete
	if (tag2delete)
		wpr.delete(user_client, tag2delete.asIObject())*/
}


/**
 * The file should be formated without any heading and each line as following : tag\n
 * */
def readTagCSV(file){

	List<String> tagsList = new ArrayList()
	
	file.eachLine { line ->
  		rowDataPoints = line.split(",")
  		tagsList.add(rowDataPoints[0])
	}
	
	return tagsList	
}


/*
 * imports  
 */

import fr.igred.omero.*
import fr.igred.omero.roi.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import fr.igred.omero.meta.*
import omero.gateway.model.DatasetData;
import omero.gateway.model.TagAnnotationData;
import omero.model.NamedValue
import ij.*
import ij.plugin.*
import ij.gui.PointRoi
import java.io.*;
import ij.gui.GenericDialog
import java.awt.Panel