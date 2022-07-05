#@String(label="Username" , value = "biopstaff") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=true) PASSWORD
#@String(label="The tag to delete") tag_string

Client user_client = new Client();
user_client.connect("omero-server.epfl.ch", 4064, USERNAME, PASSWORD.toCharArray() );
println "Connection to Omero : Success"

try{

	process(user_client)

} finally{
	user_client.disconnect()
	println "Disconnection to Omero , user: Success"
}

def process(user_client){
	
	println user_client.getUser().getId()
	
	tag_wpr_list = user_client.getTags()
	println tag_wpr_list
	tag_wpr_list.each{ tag_wpr ->
		// check that user is owner
		if  (tag_wpr.getOwner().getId() == user_client.getUser().getId() ){
			if (tag_wpr.getName() == tag_string){
				println tag_wpr.getName() + " will be deleted"
				// user_client.delete(tag_wpr) // BE SURE before you uncomment this line ! 
			} else { 
				println tag_wpr.getName() + " will NOT be deleted"
			}
			//println tag_wpr.getId()
		}else{
			println tag_wpr.getName() + " NOT owned and will NOT be deleted"
		}
	}
	
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