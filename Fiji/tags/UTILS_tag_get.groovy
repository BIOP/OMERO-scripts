#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@Long(label="ID", value=119273) id
#@String(label="Object", choices={"image","dataset","project","well","plate","screen"}) object_type

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
		println "Disonnected "+host
	}
	println "Processing of key-values for "+object_type+ " "+id+" : DONE !"
	return
	
}else{
	println "Not able to connect to "+host
}

def processTag(user_client, repository_wpr){
	
	// get image tags
	List<TagAnnotationWrapper> image_tags = repository_wpr.getTags(user_client)
	// sort tags
	image_tags.sort{it.getName()}
	// print tags
	println object_type+" tags"
	image_tags.each{println "Name : " +it.getName()+" (id : "+it.getId()+")"}
	
	
	// get all tags within the group
	List<TagAnnotationWrapper> group_tags = user_client.getTags()
	// sort tags
	group_tags.sort{it.getName()}
	// print tags
	println "\ngroup tags"
	group_tags.each{println "Name : " +it.getName()+" (id : "+it.getId()+")"}
}



import ij.*
import ij.plugin.*
import ij.ImagePlus
import ij.plugin.Concatenator
import ij.io.FileSaver
import ij.measure.ResultsTable

import fr.igred.omero.*
import fr.igred.omero.roi.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import fr.igred.omero.meta.*

import org.apache.commons.io.FilenameUtils

import omero.gateway.model.*

import ij.gui.Roi
import java.util.*