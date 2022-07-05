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
	
	raw_tag = user_client.getTags().find{ it.getName().equals(tag_string) } ?: new TagAnnotationData(tag_string)
	//tag_wpr = user_client.getTags().find{ it.getName().equals(tag_string) }
	println raw_tag
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