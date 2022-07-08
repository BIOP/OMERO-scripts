#@String(label="Username", value="dornier", persist=false) USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@Long(label="Image ID", value=119273) id


// create the client and connect to the host
Client user_client = new Client()
host = "omero-server.epfl.ch"
port = 4064

user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "Connected "+ host
	
	try{
		// Create the image Wrapper object
		ImageWrapper img_wpr = user_client.getImage(id)
		
		// Get the well continaing the image
		def well_wpr_list = image_wpr.getWells(user_client)
		def well_wpr = well_wpr_list.get(0)
		println ("Well_name : "+well_wpr.getName() +" / id : "+ well_wpr.getId())
		
		// Get the plate continaing the image
		def plate_wpr_list = image_wpr.getPlates(user_client)
		def plate_wpr = plate_wpr_list.get(0)
		println ("plate_name : "+plate_wpr.getName() + " / id : "+ plate_wpr.getId())

		// Get the screen continaing the image		
		def screen_wpr_list = image_wpr.getScreens(user_client)
		def screen_wpr = screen_wpr_list.get(0)
		println ("screen_name : "+screen_wpr.getName() + " / id : "+ screen_wpr.getId())
		
	} finally{
		user_client.disconnect()
		println "Disonnected from "+host
	}
	
}


import fr.igred.omero.*
import fr.igred.omero.repository.*
import omero.gateway.model.DatasetData;
