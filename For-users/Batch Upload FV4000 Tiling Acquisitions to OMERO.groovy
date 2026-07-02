#@String(label="Host", value="omero-server.epfl.ch", persist=true) host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD

/*  
 * This script batch uploads tiling acqusition(s) from FV4000 microscope. 
 * Select one or more tiling folders and indicate in which project/dataset you want to upload them.
 * Tiles AND/OR stitch image AND/OR info files are then uploaded on OMERO.
 * Optionally, you can select whether you wan to tag images or not during the upload.
 * 
 * If you select to upload the stitch image or the attachment, it is them renamed on OMERO to match the naming convention of the tiles. 
 * (i.e. adding the name of the parent folder as prefix)
 * 		
 * == INPUTS ==
 *  - credentials 
 *  - folder(s) to import
 *  - Tile/stich/attachment choice
 *  - Project / dataset on OMERO (you can create new ones)
 *  - Tags
 *  
 *  The GUI has a button "Next". It is here to allow you to upload different tiling acqusitions in different project/dataset.
 *  When no more tiling acqusitions need to be uploaded, click on "Finish"
 * 	
 * 
 * == OUTPUTS ==	
 *  - Images on OMERO, tagged
 *  - CSV report in the Download folder
 *  - Logs in the Fiji Log window
 * 
 * 
 * = DEPENDENCIES =
 *  - omero-ij-5.8.3-all
 *  - simple-omero-client-5.18.0 https://github.com/GReD-Clermont/simple-omero-client
 *  
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier - EPFL - SV - PTECH - BIOP
 * 
 * = PROJECT INFORMATION =
 * date : 2024.05.06
 * version : v1.0.2
 * 
 * = HISTORY =
 * - 2024.05.06 : First release --v1.0
 * - 2024.05.10 : Update token separtor --v1.0.1
 * - 2025.09.10 : Save Fiji log window --v1.0.2
 * - 2025.09.10 : Adding host in UI --v1.0.2
 * 
 */

hasFailed = false
hasSilentlyFailed = false
endedByUser = false
message = ""
tokenSeparator = " | "
csvSeparator = ","

// constants for csv file
PAR_FOL = "Parent folder"
PRJ_ID = "Project ID"
DST_ID = "Dataset ID"
PRJ = "Project name"
DST = "Dataset name"
IMG_NAME = "Image name"
IMG_PATH = "Image Path"
IMG_ID = "Image ID"
TYPE = "Type"
STS = "Status"
TAG = "Tags"
ATT = "Attachments"
NME = "Renaming from -> to"

// constants for GUI
String FOL_PATH = "path";
String OMR_PRJ = "project";
String OMR_DST = "dataset";
String IS_NEW_DST = "isNewDataset";
String IS_DST_FLD = "isNewFromFolder";
String IS_NEW_PRJ = "isNewProject";
String DST_NAME = "datasetName";
String PRJ_NAME = "projectName";
String TLE_IMP = "tile";
String STC_IMP = "stitch";
String ATT_IMP = "attachmentImport";
String TAG_TLE = "tagTiles";
String TAG_FOL = "tagTilingFolder";

// Connection to server
port = 4064
Client user_client = new Client()

try{
	user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())
}catch(Exception e){
	IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
	def message = "Cannot connect to "+host+". Please check your credentials"
	JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
	return
}

if (user_client.isConnected()){
	IJLoggerInfo("OMERO","Connected to "+host)
	List<Map<String, String>> transferSummary = new ArrayList<>()
	Map<String, String> commonSummaryMap = new HashMap<>()
	def countImg = 0;
	
	try{		
		
		// get the userID
		def userId = user_client.getId()
		
		// get user's projects
		def projectWrapperList = user_client.getProjects(user_client.getUser())
		
		// get project's name
		def projectNames = (String[])projectWrapperList.stream().map(ProjectWrapper::getName).collect(Collectors.toList()).sort()
		
		// generate the dialog box
		def dialog = new Dialog(user_client, projectNames, projectWrapperList, userId)
	
		while(!dialog.getEnterPressed()){
	   		// Wait an answer from the user (Ok or Cancel)
		}
		
		// If Ok
		if(dialog.getValidated()){		
			for(Map<String, String> selectedMap : dialog.getSelectedList()){
				
				// collect the user inputs
				String inputValues = selectedMap.collect{key, value -> ""+key+":"+value}.join("\n")
				IJLoggerInfo("LOCAL", "User input\n " + inputValues);
			
				String foldersToImport = selectedMap.get(FOL_PATH)
				boolean isNewDataset = selectedMap.get(IS_NEW_DST).toLowerCase().equals("true") ? true: false
				boolean isNewFromFolder = selectedMap.get(IS_DST_FLD).toLowerCase().equals("true") ? true: false
				boolean isNewProject = selectedMap.get(IS_NEW_PRJ).toLowerCase().equals("true") ? true: false
				String existingProjectName = selectedMap.get(OMR_PRJ)
				String existingDatasetName = selectedMap.get(OMR_DST)
				String newProjectName = selectedMap.get(PRJ_NAME)
				String newDatasetName = selectedMap.get(DST_NAME)
				boolean isTileImport = selectedMap.get(TLE_IMP).toLowerCase().equals("true") ? true: false
				boolean isStitchImport = selectedMap.get(STC_IMP).toLowerCase().equals("true") ? true: false
				boolean isAttachmentImport = selectedMap.get(ATT_IMP).toLowerCase().equals("true") ? true: false
				boolean isTagTile = selectedMap.get(TAG_TLE).toLowerCase().equals("true") ? true: false
				boolean isTagFolder = selectedMap.get(TAG_FOL).toLowerCase().equals("true") ? true: false
			
				// getting the project
				def projectWrapper
				if(!isNewProject){
					IJLoggerInfo("OMERO", "Getting the project '" + existingProjectName +"'");
					projectWrapper = projectWrapperList.find{it.getName() == existingProjectName}
				}else{
					try{
						IJLoggerInfo("OMERO", "Creating a new project '" + newProjectName +"'");
						projectWrapper = createOmeroProject(user_client, newProjectName)
						projectWrapperList.add(projectWrapper)
					}catch (Exception e){
						hasSilentlyFailed = true
						message = "The project '"+newProjectName+"' cannot be created on OMERO."
						IJLoggerError("OMERO", message, e)
						foldersToImport.split(",").each{
							Map<String, String> imgSummaryMap = new HashMap<>()
							File folder = new File(it)
							imgSummaryMap.put(PAR_FOL, folder.getName())
							imgSummaryMap.putAll(commonSummaryMap)
							transferSummary.add(imgSummaryMap)
						}
						continue
					}
				}
				
				commonSummaryMap.put(PRJ, projectWrapper.getName())
				commonSummaryMap.put(PRJ_ID, ""+projectWrapper.getId())
				
				Map<String, DatasetWrapper> datasetsList = new HashMap<>()
				def datasetWrapper
				
				for(String folderPath : foldersToImport.split(",")){
					Map<String, String> datasetSummaryMap = new HashMap<>()
					File folder = new File(folderPath)
					
					commonSummaryMap.put(PAR_FOL, folder.getName())
					
					// getting the dataset
					
					if(isNewFromFolder){
						isNewDataset = true
						datasetWrapper = null;
						newDatasetName = folder.getName()
					}
					
					// getting the dataset
					if(datasetWrapper == null){
						if(!isNewDataset){
							try{
								IJLoggerInfo("OMERO", "Getting the dataset '" + existingDatasetName +"'");
								if(datasetsList.containsKey(existingDatasetName))
									datasetWrapper = datasetsList.get(existingDatasetName)
								else{
									datasetWrapper = projectWrapper.getDatasets(existingDatasetName).get(0)
									datasetsList.put(existingDatasetName, datasetWrapper)
								}
							}catch (Exception e){
								hasSilentlyFailed = true
								message = "The dataset '"+existingDatasetName+"' cannot be retrieved from OMERO."
								IJLoggerError("OMERO", message, e)
								Map<String, String> imgSummaryMap = new HashMap<>()
								imgSummaryMap.putAll(commonSummaryMap)
								transferSummary.add(imgSummaryMap)
								continue
							}
						} else {
							try{				
								IJLoggerInfo("OMERO", "Creating a new dataset '" + newDatasetName +"'");
								datasetWrapper = createOmeroDataset(user_client, projectWrapper, newDatasetName)
								datasetsList.put(newDatasetName, datasetWrapper)
							}catch (Exception e){
								hasSilentlyFailed = true
								message = "The dataset '"+newDatasetName+"' cannot be created on OMERO."
								IJLoggerError("OMERO", message, e)
								Map<String, String> imgSummaryMap = new HashMap<>()
								imgSummaryMap.putAll(commonSummaryMap)
								transferSummary.add(imgSummaryMap)
								continue
							}
						}
					}
					
					commonSummaryMap.put(DST, datasetWrapper.getName())
					commonSummaryMap.put(DST_ID, ""+datasetWrapper.getId())
					
					
					// getting files to Upload
					Map<String, List<File>> filesToUploadMap = new HashMap<>()
					if(isTileImport)
						filesToUploadMap.put(TLE_IMP, listFiles(folder, /.*_.[\w]*.[\d]*_.[\w]*.[\d]*_.[\d]*.oir/))
					if(isStitchImport)
						filesToUploadMap.put(STC_IMP, listFiles(folder, /Stitch_.[\w]*.[\d]*_.[\w]*.[\d]*.oir/))
					
					if(filesToUploadMap.isEmpty()){
						IJLoggerWarn("LOCAL", "Folder '"+folder.getName()+"' does not contains any image(s). Nothing to upload")
						Map<String, String> imgSummaryMap = new HashMap<>()
						imgSummaryMap.putAll(commonSummaryMap)
						transferSummary.add(imgSummaryMap)
						continue
					}
					
					for(String imgType : filesToUploadMap.keySet()){
						IJLoggerInfo("Working on "+imgType+" images")
						
						List<String> tagsToLink = new ArrayList<>()
						
						// set the tags to upload
						if(isTagTile)
							tagsToLink.add(imgType)
						if(isTagFolder)
							tagsToLink.add(folder.getName())	
						
						// get files to uplaod
						def filesToUpload = filesToUploadMap.get(imgType)
						if(filesToUpload.isEmpty()){
							IJLoggerWarn("LOCAL", "Folder '"+folder.getName()+"' does not contains any "+imgType+" image(s). Nothing to upload")
							Map<String, String> imgSummaryMap = new HashMap<>()
							imgSummaryMap.putAll(commonSummaryMap)
							imgSummaryMap.put(TYPE, imgType)
							transferSummary.add(imgSummaryMap)
							continue
						}
						
						for(File imageToUpload : filesToUpload){
							IJLoggerInfo("Working on image '"+imageToUpload.getName()+"'...")
							Map<String, String> imgSummaryMap = new HashMap<>()
							imgSummaryMap.put(IMG_NAME, imageToUpload.getName())
							imgSummaryMap.put(IMG_PATH, imageToUpload.getAbsolutePath())
							imgSummaryMap.put(TYPE, imgType)
							imgSummaryMap.putAll(commonSummaryMap)
							
							// upload image
							def ids = []
							try{
								IJLoggerInfo("OMERO","Upload on OMERO...")
								ids = datasetWrapper.importImage(user_client, imageToUpload.getAbsolutePath())
								if(ids != null){
									countImg += ids.size()
									imgSummaryMap.put(IMG_ID, ids.join(tokenSeparator))
									imgSummaryMap.put(STS, "Uploaded")
								}
							}catch(Exception e){
								hasSilentlyFailed = true
				    			message = "Impossible to upload on OMERO"						
								IJLoggerError("OMERO", message, e)
								imgSummaryMap.put(STS, "Failed")
								transferSummary.add(imgSummaryMap)
								continue
							}
							
							// prevent loading images from OMERO if it is not necessary
							if(imgType.equals(STC_IMP) || !tagsToLink.isEmpty()){
								// get the OMERO image wrapper
								ImageWrapper imageWpr = null
								def id = ids.get(0)
								try{
									IJLoggerInfo("OMERO", "Reading image '"+id+"' from OMERO")
									imageWpr = user_client.getImage(id)
								}catch(Exception e){
									hasSilentlyFailed = true
					    			message = "Impossible to read the image '"+id+"'. Cannot link tags neither attachmenets"
									IJLoggerError("OMERO", message, e)
								}
							
								if(imageWpr != null){
									if(!tagsToLink.isEmpty()){
										// link tags
										try{
											IJLoggerInfo("OMERO", "Linking tags to image '"+imageWpr.getName()+"'...")
											saveTagsOnOmero(user_client, imageWpr, tagsToLink)
											imgSummaryMap.put(TAG, tagsToLink.join(tokenSeparator))
										}catch(Exception e){
											hasSilentlyFailed = true
							    			message = "Impossible to link tags to image '"+imageWpr.getName()+"'"
											IJLoggerError("OMERO", message, e)
										}
									}
									
									if(imgType.equals(STC_IMP)){
										// rename stitch image
										try{
											def oldName = imageWpr.getName()
											def newName = folder.getName()+"_"+oldName
											IJLoggerInfo("OMERO", "Renaming the stitch image from '" + oldName + "' to '" + newName + "'")
											imageWpr.setName(newName)
											user_client.getDm().updateObject(user_client.getCtx(), imageWpr.asDataObject().asIObject(), null);
											imgSummaryMap.put(NME, oldName + " -> " + newName)
										}catch(Exception e){
											hasSilentlyFailed = true
							    			message = "Impossible to rename the stitch image"
											IJLoggerError("OMERO", message, e)
										}
									}
								}
							}else{
								IJLoggerInfo("OMERO", "No tag to link")
							}
							
							transferSummary.add(imgSummaryMap)
						}
					}
					
					// attach info files
					if(isAttachmentImport){
						IJLoggerInfo("LOCAL","Getting attachment files")
						List<File> attachments = listFiles(folder, /.*.omp2info/)
						Map<String, String> dstSummaryMap = new HashMap<>()
						dstSummaryMap.putAll(commonSummaryMap)
						List<String> linkedAttachments = new ArrayList<>()
						
						for(File attachment : attachments){
							try{
								IJLoggerInfo("OMERO", "Attach files to dataset '"+datasetWrapper.getName()+"'...")
								boolean linked = saveAttachmentOnOmero(user_client, datasetWrapper, attachment, folder.getName())
								if(linked)
									linkedAttachments.add(attachment.getName())
								else linkedAttachments.add("Failed")
							}catch(Exception e){
								hasSilentlyFailed = true
				    			message = "Impossible to attach files to dataset '"+datasetWrapper.getName()+"'"
								IJLoggerError("OMERO", message, e)
								linkedAttachments.add("Failed")
							}
						}
						
						dstSummaryMap.put(ATT, linkedAttachments.join(tokenSeparator))
						transferSummary.add(dstSummaryMap)
					}else{
						IJLoggerInfo("OMERO", "Info files are not attached to the dataset")
					}
				}
			}

			IJLoggerInfo("OMERO", countImg + " images uploaded on OMERO");
			
			if(hasSilentlyFailed)
				message = "The script ended with some errors."
			else 
				message = "The upload and tagging have been successfully done."
		}else{
			IJLoggerInfo("LOCAL", "The script was ended by the user")
			endedByUser = true
		}

	}catch(Exception e){
		IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
		if(!hasFailed){
			hasFailed = true
			message = "An error has occurred. Please look at the logs and the report to know where the processing has failed."
		}
	}finally{
		// generate CSV report
		try{
			if(!endedByUser){
				IJLoggerInfo("CSV report", "Generate the CSV report...")
				generateCSVReport(transferSummary)
			}
		}catch(Exception e2){
			IJLoggerError(e2.toString(), "\n"+getErrorStackTraceAsString(e2))
			hasFailed = true
			message += " An error has occurred during csv report generation."
		}finally{
			// disconnect
			user_client.disconnect()
			IJLoggerInfo("OMERO","Disconnected from "+host)
			
			// print final popup
			if(!endedByUser){
				if(!hasFailed) {
					message += " A CSV report has been created in your 'Downloads' folder."
					if(hasSilentlyFailed){
						JOptionPane.showMessageDialog(null, message, "The end", JOptionPane.WARNING_MESSAGE);
					}else{
						JOptionPane.showMessageDialog(null, message, "The end", JOptionPane.INFORMATION_MESSAGE);
					}
				}else{
					JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
				}
			}
		}
	}
}else{
	message = "Not able to connect to "+host
	IJLoggerError("OMERO", message)
	JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
}
return



/**
 * list tile images
 */
def listFiles(parentFolder, pattern){
	def imgList = []
	def regex = Pattern.compile(pattern)
	
	parentFolder.listFiles().each{imgFile->
		// check for file
		if(imgFile.isFile()){
			// remove all hidden files
			if(!imgFile.isHidden()){
				def imgName = imgFile.getName()
				// check file compatibility
				def matcher = regex.matcher(imgName)
				if(matcher.find()){
					imgList.add(imgFile)
				}
			}
		}
	}
	return imgList
}

/**
 * return the project wrapper corresonding a new project or to an existing project, if it already exists
 */
def createOmeroProject(user_client, projectName){
	// if specified, create a new project on OMERO
    def project = new ProjectData();
	project.setName(projectName);
    
    // create the dataset on OMERO
    project = (ProjectData) user_client.getDm().saveAndReturnObject(user_client.getCtx(), project)
    def newId = project.getId()
    
    // create the corresponding wrapper
    def projectWrapper = user_client.getProject(newId)
	
	return projectWrapper
}


/**
 * return the dataset wrapper corresonding a new dataset or to an existing dataset, if it already exists
 */
def createOmeroDataset(user_client, projectWrapper, datasetName){
    def dataset = new DatasetData();
	dataset.setName(datasetName);
    
    // create the dataset on OMERO
    dataset = user_client.getDm().createDataset(user_client.getCtx(), dataset, projectWrapper.asProjectData())
    def newId = dataset.getId()
    
    // create the corresponding wrapper
    def datasetWrapper = user_client.getDataset(newId)
	
	return datasetWrapper
}


/**
 * Upload the image on OMERO, in the specified dataset
 * 
 */
def saveAttachmentOnOmero(user_client, datasetWrapper, attachment, tilingName){
    String home = Prefs.getHomeDir();

    // Copy the attachment 
    File copiedFile = new File(home + File.separator + attachment.getName())
    Files.copy(attachment.toPath(), copiedFile.toPath())
    
    // check if the attachment has been copied
    if(copiedFile == null || !copiedFile.exists()) {
        IJLoggerError("LOCAL", "Cannot save attachment "+attachment.getAbsolutePath()+" in " + home);
        IJLoggerError("OMERO", "The attachment '"+attachment.getName()+"' will not be uploaded on OMERO")
        return false;
    }
    
    File renamedCopiedFile = null	
    try {
    	// rename the attachment file
    	renamedCopiedFile = new File(home + File.separator + tilingName + "_"+ attachment.getName())
    	copiedFile.renameTo(renamedCopiedFile); 
    	
        // Import attachment on OMERO
        datasetWrapper.addFile(user_client, renamedCopiedFile)
    } catch (Exception e) {
        IJLoggerError("OMERO", "Cannot upload attachment '"+renamedCopiedFile.getName()+"' on OMERO", e);
    }

    // delete the file after upload
    boolean hasBeenDeleted
    if(renamedCopiedFile != null)
	    hasBeenDeleted = renamedCopiedFile.delete();
    else
    	hasBeenDeleted = copiedFile.delete();
    
    if(hasBeenDeleted)
	        IJLoggerInfo("LOCAL","Temporary file deleted");
	else IJLoggerError("LOCAL", "Cannot delete temporary file from "+renamedCopiedFile.getAbsolutePath());
	
	return true
}
 
/**
 * Add a list of tags to the specified image
 */
def saveTagsOnOmero(user_client, imgWpr, tags){
	def tagsToAdd = []
	
	// get existing tags
	def groupTags = user_client.getTags()
	def imageTags = imgWpr.getTags(user_client)
	
	// find if the tag to add already exists on OMERO. If yes, they are not added twice
	tags.each{tag->
		if(tagsToAdd.find{ it.getName().toLowerCase().equals(tag.toLowerCase()) } == null){
			// find if the requested tag already exists
			new_tag = groupTags.find{ it.getName().toLowerCase().equals(tag.toLowerCase()) } ?: new TagAnnotationWrapper(new TagAnnotationData(tag))
			
			// add the tag if it is not already the case
			imageTags.find{ it.getName().toLowerCase().equals(new_tag.getName().toLowerCase()) } ?: tagsToAdd.add(new_tag)
		}	
	}
	
	// add all tags to the image
	imgWpr.addTags(user_client, (TagAnnotationWrapper[])tagsToAdd.toArray())
}


/**
 * Create the CSV report from all info collecting during the processing
 */
def generateCSVReport(transferSummaryList){
	// define the header
	def headerList = [PAR_FOL, PRJ, PRJ_ID, DST, DST_ID, IMG_NAME, IMG_PATH, TYPE, STS, IMG_ID, TAG, NME, ATT]
	String header = headerList.join(csvSeparator)
	String statusOverallSummary = ""
	
	// get all summaries
	transferSummaryList.each{imgSummaryMap -> 
		def statusSummaryList = []
		//loop over the parameters
		headerList.each{outputParam->
			if(imgSummaryMap.containsKey(outputParam))
				statusSummaryList.add(imgSummaryMap.get(outputParam))
			else
				statusSummaryList.add("-")
		}
		
		statusOverallSummary += statusSummaryList.join(csvSeparator) + "\n"
	}
	String content = header + "\n"+statusOverallSummary
					
	// save the report
	def name = getCurrentDateAndHour()+"_FV4000_Tiling_Upload_to_OMERO_report"
	String path = System.getProperty("user.home") + File.separator +"Downloads"
	IJLoggerInfo("CSV report", "Saving the report as '"+name+".csv' in "+path+"....")
	writeCSVFile(path, name, content)	
	IJLoggerInfo("CSV report", "DONE!")
		
	// save the log window
    saveFijiLogWindow(path, name)
}


/**
 * Save a csv file in the given path, with the given name
 */
def writeCSVFile(path, name, fileContent){
	// create the file locally
    File file = new File(path.toString() + File.separator + name + ".csv");

    try (BufferedWriter buffer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
        buffer.write(fileContent);
	}catch(Exception e){
		throw e
	}
}


/**
 * Saves the Log of Fiji
 */
def saveFijiLogWindow(path, name){
	// create the path locally
    String filePath = path.toString() + File.separator + name + "_logs.txt";

	// select the log window
    IJ.selectWindow("Log")
    
    // save it
	IJ.saveAs("Text", filePath);
}


/**
 * 
 * Create the Dialog asking for the project and dataset
 * 
 * */
public class Dialog extends JFrame {
	
	private JComboBox<String> cmbProject;
    private JComboBox<String> cmbDataset;
    private JButton bnOk = new JButton("Finish");
    private JButton bnCancel = new JButton("Cancel");
    private JButton bnNext = new JButton("Next");
    private DefaultComboBoxModel<String> modelCmbProject;
    private DefaultComboBoxModel<String> modelCmbDataset;
    	
	Client client;
	def userId;
	def project_list;
	boolean enterPressed;
	boolean validated;
	
	String DEFAULT_PATH_KEY = "scriptDefaultDir"
	String FOL_PATH = "path";
    String OMR_PRJ = "project";
    String OMR_DST = "dataset";
    String IS_NEW_DST = "isNewDataset";
    String IS_DST_FLD = "isNewFromFolder";
    String IS_NEW_PRJ = "isNewProject";
    String DST_NAME = "datasetName";
    String PRJ_NAME = "projectName";
    String TLE_IMP = "tile";
    String STC_IMP = "stitch";
    String ATT_IMP = "attachmentImport";
    String TAG_TLE = "tagTiles";
    String TAG_FOL = "tagTilingFolder";
    
	File currentDir = IJ.getProperty(DEFAULT_PATH_KEY) == null ? new File("") : ((File)IJ.getProperty(DEFAULT_PATH_KEY))
	List<Map<String, String>> selectionList = new ArrayList<>()
	Map<String, List<String>> projectNewDatasets = new HashMap<>()
	
	public Dialog(user_client, project_names, project_list, userId){
		client = user_client
		this.userId = userId
		this.project_list = project_list
		
		project_names.each{
			projectNewDatasets.put(it, new ArrayList<>())
		}
		
    	def project = project_list.find{it.getName() == project_names[0]}
    	def dataset_list = project.getDatasets()
		def dataset_names = dataset_list.stream().map(DatasetWrapper::getName).collect(Collectors.toList())
		projectNewDatasets.put(project_names[0], dataset_names)
		
		myDialog(project_names)
	}
	
	// getters
	public boolean getEnterPressed(){return this.enterPressed}
	public boolean getValidated(){return this.validated}
	public List<Map<String, String>> getSelectedList(){return this.selectionList}
	
	// generate the dialog box
	public void myDialog(project_names) {
		// set general frame
		this.setTitle("Select your import location on OMERO")
	    this.setVisible(true);
	    this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
	   // this.setPreferredSize(new Dimension(400, 250));
	    
	    // get the screen size
	    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        double width = screenSize.getWidth();
        double height = screenSize.getHeight();
        
        // set location in the middle of the screen
	    this.setLocation((int)((width - 400)/2), (int)((height - 250)/2));
		
		// build project combo model
		modelCmbProject = new DefaultComboBoxModel<>(project_names);
        cmbProject = new JComboBox<>(modelCmbProject);
        
        // build dataset combo model
		modelCmbDataset = new DefaultComboBoxModel<>((String[])projectNewDatasets.get(project_names[0]).sort());
        cmbDataset = new JComboBox<>(modelCmbDataset);
        cmbDataset.setEnabled(false)
		
		// checkbox to import tiles
        JCheckBox chkTiles = new JCheckBox("Individual tiles");
        chkTiles.setSelected(true);
        
        // checkbox to import stitch image
        JCheckBox chkStitch = new JCheckBox("Stitch image");
        chkStitch.setSelected(true);
        
        // checkbox to import attachment
        JCheckBox chkAttm = new JCheckBox("Info files as attachment");
        chkAttm.setSelected(true);
            
        // label for tags
        JLabel tagLabel = new JLabel("Choose the tags you want to link to images")
        tagLabel.setAlignmentX(LEFT_ALIGNMENT)
        
        // checkbox to tag with tile/stitch
        JCheckBox chkTagTile = new JCheckBox("Tile/stitch");
        chkTagTile.setSelected(true);
		                
        // checkbox to tag with parent folder name
        JCheckBox chkTagParent = new JCheckBox("Tiling folder");
        chkTagParent.setSelected(false);
        
        // Root folder for project
        JLabel labRootFolder  = new JLabel("Tiling Folder(s)");
        JTextField tfRootFolder = new JTextField();
        tfRootFolder.setColumns(15);
        tfRootFolder.setText(currentDir.getAbsolutePath())

        // button to choose root folder
        JButton bRootFolder = new JButton("Choose folder");
        bRootFolder.addActionListener(e->{
            JFileChooser directoryChooser = new JFileChooser();
            directoryChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            directoryChooser.setCurrentDirectory(currentDir);
            directoryChooser.setMultiSelectionEnabled(true)
            directoryChooser.setDialogTitle("Choose the project folder");
            directoryChooser.showDialog(new JDialog(),"Select");

            if (directoryChooser.getSelectedFiles() != null){
                tfRootFolder.setText(directoryChooser.getSelectedFiles().join(","));
                currentDir = directoryChooser.getSelectedFile()
                IJ.setProperty(DEFAULT_PATH_KEY, currentDir)
            }
        });
		
        // build Combo project
        JPanel boxComboProject = new JPanel();
        JLabel projectLabel = new JLabel("Project");
        boxComboProject.add(projectLabel);
        boxComboProject.add(cmbProject);
        boxComboProject.setLayout(new FlowLayout());
        
         // Project name
        JLabel labProjectName = new JLabel("Project name");
        JTextField tfProjectName = new JTextField("");
        tfProjectName.setColumns(15);
        tfProjectName.setEnabled(false)
        
        // Radio button to choose existing project
        ButtonGroup projectChoice = new ButtonGroup();
        JRadioButton rbExistingProject = new JRadioButton("Existing project");
        projectChoice.add(rbExistingProject);
        rbExistingProject.setSelected(true);
        
        // Radio button to choose new project
        JRadioButton rbNewProject = new JRadioButton("New project");
        projectChoice.add(rbNewProject);
        rbNewProject.setSelected(false);        
        
        // build Combo dataset
        JPanel boxComboDataset = new JPanel();
        JLabel datasetLabel = new JLabel("Dataset");
        boxComboDataset.add(datasetLabel);
        boxComboDataset.add(cmbDataset);
        boxComboDataset.setLayout(new FlowLayout());
                
        // Dataset name
        JLabel labDatasetName = new JLabel("Dataset name");
        JTextField tfDatasetName = new JTextField("");
        tfDatasetName.setColumns(15);
        
        // Radio button to choose existing dataset
        ButtonGroup datasetChoice = new ButtonGroup();
        JRadioButton rbExistingDataset = new JRadioButton("Existing dataset");
        datasetChoice.add(rbExistingDataset);
        rbExistingDataset.setSelected(false);
        rbExistingDataset.addActionListener(e -> {
			cmbDataset.setEnabled(rbExistingDataset.isSelected());
			tfDatasetName.setEnabled(!rbExistingDataset.isSelected())
        });
        
         // Radio button to choose new dataset
        JRadioButton rbNewDataset = new JRadioButton("New dataset");
        datasetChoice.add(rbNewDataset);
        rbNewDataset.setSelected(true);
        rbNewDataset.addActionListener(e -> {
			cmbDataset.setEnabled(!rbNewDataset.isSelected());
			tfDatasetName.setEnabled(rbNewDataset.isSelected())
        });
        
        
         // checkbox to tag with parent folder name
        JCheckBox chkNewFromFolder = new JCheckBox("New from folder");
        chkNewFromFolder.setSelected(false);
        chkNewFromFolder.addActionListener(e -> {
			cmbDataset.setEnabled(!chkNewFromFolder.isSelected() && !rbNewDataset.isSelected());
			tfDatasetName.setEnabled(!chkNewFromFolder.isSelected() && !rbExistingDataset.isSelected())
			rbExistingDataset.setEnabled(!chkNewFromFolder.isSelected() && !rbNewProject.isSelected())
			rbNewDataset.setEnabled(!chkNewFromFolder.isSelected())
        });
        
        // actionListener on new project radio button
        rbNewProject.addActionListener(e -> {
			cmbProject.setEnabled(!rbNewProject.isSelected());
			tfProjectName.setEnabled(rbNewProject.isSelected())
			rbExistingDataset.setSelected(!rbNewProject.isSelected());
			rbNewDataset.setSelected(rbNewProject.isSelected());
			rbExistingDataset.setEnabled(!rbNewProject.isSelected());
			tfDatasetName.setEnabled(rbNewProject.isSelected() && !chkNewFromFolder.isSelected())
			cmbDataset.setEnabled(!rbNewProject.isSelected());
        });
        
        // actionListener on existing project radio button
        rbExistingProject.addActionListener(e -> {
			cmbProject.setEnabled(rbExistingProject.isSelected());
			tfProjectName.setEnabled(!rbExistingProject.isSelected())
			rbExistingDataset.setSelected(!rbExistingProject.isSelected());
			rbNewDataset.setSelected(rbExistingProject.isSelected());
			rbExistingDataset.setEnabled(rbExistingProject.isSelected() && !chkNewFromFolder.isSelected());
        });
              
        // build buttons
        JPanel boxButton = new JPanel();
        boxButton.add(bnNext);
        boxButton.add(bnOk);
        boxButton.add(bnCancel);
        boxButton.setLayout(new FlowLayout());
        
        // radioBox Dataset
        JPanel windowRadioDataset = new JPanel();
        windowRadioDataset.setLayout(new BoxLayout(windowRadioDataset, BoxLayout.X_AXIS));
        windowRadioDataset.add(rbExistingDataset);
        windowRadioDataset.add(rbNewDataset);
        windowRadioDataset.add(chkNewFromFolder);
        
         // radioBox Project
        JPanel windowRadioProject = new JPanel();
        windowRadioProject.setLayout(new BoxLayout(windowRadioProject, BoxLayout.X_AXIS));
        windowRadioProject.add(rbExistingProject);
        windowRadioProject.add(rbNewProject);
        
        // Dataset Name Box
        JPanel windowDataset = new JPanel();
        windowDataset.setLayout(new BoxLayout(windowDataset, BoxLayout.X_AXIS));
        windowDataset.add(labDatasetName);
        windowDataset.add(tfDatasetName);
        
        // Project Name Box
        JPanel windowProject = new JPanel();
        windowProject.setLayout(new BoxLayout(windowProject, BoxLayout.X_AXIS));
        windowProject.add(labProjectName);
        windowProject.add(tfProjectName);
        
         // Folder box
        JPanel windowFolder = new JPanel();
        windowFolder.setLayout(new BoxLayout(windowFolder, BoxLayout.X_AXIS));
        windowFolder.add(labRootFolder);
        windowFolder.add(tfRootFolder);
        windowFolder.add(bRootFolder);
        
        // general panel
        JPanel windowNLGeneral = new JPanel();
        windowNLGeneral.setLayout(new BoxLayout(windowNLGeneral, BoxLayout.Y_AXIS));
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(windowFolder);
        windowNLGeneral.add(chkTiles);
        windowNLGeneral.add(chkStitch);
        windowNLGeneral.add(chkAttm);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(new JSeparator());
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(windowRadioProject);
        windowNLGeneral.add(boxComboProject);
        windowNLGeneral.add(windowProject);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(new JSeparator());
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(windowRadioDataset);
        windowNLGeneral.add(boxComboDataset);
        windowNLGeneral.add(windowDataset);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(new JSeparator());
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(tagLabel);
		windowNLGeneral.add(chkTagTile);
        windowNLGeneral.add(chkTagParent);
        windowNLGeneral.add(new JSeparator());
        windowNLGeneral.add(boxButton);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        
        JPanel nicerWindow = new JPanel();
        nicerWindow.setLayout(new BoxLayout(nicerWindow, BoxLayout.X_AXIS));
        nicerWindow.add(Box.createRigidArea(new Dimension(5,0)));
        nicerWindow.add(windowNLGeneral);
        nicerWindow.add(Box.createRigidArea(new Dimension(5,0)));
        
        // add listener on project combo box
        cmbProject.addItemListener(
			new ItemListener(){
				    @Override
			    public void itemStateChanged(ItemEvent e) {
					// get the datasets corresponding to the selected project
			        def chosen_project = (String) cmbProject.getSelectedItem()
			        def dataset_names
			        if(projectNewDatasets.get(chosen_project).isEmpty()){
			        	def project = project_list.find{it.getName() == chosen_project}
						def dataset_list = project.getDatasets()
						dataset_names = dataset_list.stream().map(DatasetWrapper::getName).collect(Collectors.toList()).sort()
						projectNewDatasets.put(chosen_project, dataset_names)
			        }else{
			        	dataset_names = projectNewDatasets.get(chosen_project)
			        }
			        
			        // update the dataset combo box
					modelCmbDataset.removeAllElements();
        			for (String dataset : dataset_names) modelCmbDataset.addElement(dataset);
        			cmbDataset.setSelectedIndex(0);
			    }
			}
		);
		
		// add listener on Ok and Cancel button
		bnOk.addActionListener(
			new ActionListener(){
				@Override
    			public void actionPerformed(ActionEvent e) {
    				
    				def rootFolder = (String)tfRootFolder.getText()
    				if(rootFolder != null && !rootFolder.isEmpty()){
						
						if(!checkInputs(tfRootFolder, rbNewDataset, chkNewFromFolder, tfDatasetName, rbNewProject, tfProjectName, chkTiles, chkStitch))
							return
		
						Map<String, String> selection = new HashMap<>()
						selection.put(FOL_PATH, rootFolder)
						selection.put(OMR_PRJ, (String) cmbProject.getSelectedItem())
						selection.put(OMR_DST, (String) cmbDataset.getSelectedItem())
						selection.put(IS_NEW_DST, String.valueOf(rbNewDataset.isSelected()))
						selection.put(IS_DST_FLD, String.valueOf(chkNewFromFolder.isSelected()))
						selection.put(IS_NEW_PRJ, String.valueOf(rbNewProject.isSelected()))
						selection.put(TLE_IMP, String.valueOf(chkTiles.isSelected()))
						selection.put(STC_IMP, String.valueOf(chkStitch.isSelected()))
						selection.put(ATT_IMP, String.valueOf(chkAttm.isSelected()))
						selection.put(DST_NAME, (String) tfDatasetName.getText())
						selection.put(PRJ_NAME, (String) tfProjectName.getText())
						selection.put(TAG_TLE, String.valueOf(chkTagTile.isSelected()))
						selection.put(TAG_FOL, String.valueOf(chkTagParent.isSelected()))
						selectionList.add(selection)
    				}

    				enterPressed = true
    				validated = true;
    				
    				this.dispose()
    			}
			}
		)
		
		bnCancel.addActionListener(
			new ActionListener(){
				@Override
    			public void actionPerformed(ActionEvent e) {
    				enterPressed = true
    				validated = false;
    				this.dispose()
    			}
			}
		)
		
		bnNext.addActionListener(
			new ActionListener(){
				@Override
    			public void actionPerformed(ActionEvent e) {
    				if(!checkInputs(tfRootFolder, rbNewDataset, chkNewFromFolder, tfDatasetName, rbNewProject, tfProjectName, chkTiles, chkStitch))
    					return
    				
    				Map<String, String> selection = new HashMap<>()
    				selection.put(FOL_PATH, (String) tfRootFolder.getText())
    				selection.put(OMR_PRJ, (String) cmbProject.getSelectedItem())
    				selection.put(OMR_DST, (String) cmbDataset.getSelectedItem())
    				selection.put(IS_NEW_DST, String.valueOf(rbNewDataset.isSelected()))
    				selection.put(IS_DST_FLD, String.valueOf(chkNewFromFolder.isSelected()))
    				selection.put(IS_NEW_PRJ, String.valueOf(rbNewProject.isSelected()))
    				selection.put(TLE_IMP, String.valueOf(chkTiles.isSelected()))
    				selection.put(STC_IMP, String.valueOf(chkStitch.isSelected()))
    				selection.put(ATT_IMP, String.valueOf(chkAttm.isSelected()))
    				selection.put(DST_NAME, (String) tfDatasetName.getText())
    				selection.put(PRJ_NAME, (String) tfProjectName.getText())
    				selection.put(TAG_TLE, String.valueOf(chkTagTile.isSelected()))
    				selection.put(TAG_FOL, String.valueOf(chkTagParent.isSelected()))
					selectionList.add(selection)
										
					if(rbNewProject.isSelected()){
						def prjName = (String) tfProjectName.getText()
						projectNewDatasets.put(prjName, new ArrayList<>())
	        			modelCmbProject.addElement(prjName);
					}
					
					if(rbNewDataset.isSelected() && !chkNewFromFolder.isSelected()){
						def dstName = (String) tfDatasetName.getText()
						def chosenProject = (String) cmbProject.getSelectedItem()
						if(rbNewProject.isSelected())
							chosenProject = (String) tfProjectName.getText()
							
						def tmpDatasetList = projectNewDatasets.get(chosenProject)
						tmpDatasetList.add(dstName)
						projectNewDatasets.put(chosenProject, tmpDatasetList)
						
						// update the dataset combo box
						modelCmbDataset.removeAllElements();
	        			for (String dataset : tmpDatasetList) modelCmbDataset.addElement(dataset);
	        			cmbDataset.setSelectedIndex(0);
					}
					
					tfDatasetName.setText("");
					tfProjectName.setText("");
					tfRootFolder.setText("");
    			}
			}
		)
		
		 // set main interface parameters
		this.addWindowListener(new WindowListener() {
            @Override
            public void windowOpened(WindowEvent e) {

            }

            @Override
            public void windowClosing(WindowEvent e) {

            }

            @Override
            public void windowClosed(WindowEvent e) {
				enterPressed = true
    			validated = false;
            }

            @Override
            public void windowIconified(WindowEvent e) {

            }

            @Override
            public void windowDeiconified(WindowEvent e) {

            }

            @Override
            public void windowActivated(WindowEvent e) {

            }

            @Override
            public void windowDeactivated(WindowEvent e) {

            }
        });

        this.setContentPane(nicerWindow);
        this.pack();
    }
    
    private boolean checkInputs(tfRootFolder, rbNewDataset, chkNewFromFolder, tfDatasetName, rbNewProject, tfProjectName, chkTiles, chkStitch){
    	if(tfRootFolder.getText() == null || tfRootFolder.getText().isEmpty()){
    		def message = "You must enter a folder to process"
			JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
			return false
    	}
    	
    	if(rbNewDataset.isSelected() && !chkNewFromFolder.isSelected() && (tfDatasetName.getText() == null || tfDatasetName.getText().isEmpty())){
			def message = "You must enter a name for the new dataset"
			JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
			return false
		}
		
		if(rbNewProject.isSelected() && (tfProjectName.getText() == null || tfProjectName.getText().isEmpty())){
			def message = "You must enter a name for the new project"
			JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
			return false
		}
		
		if(!(chkTiles.isSelected() || chkStitch.isSelected())){
			def message = "You must choose either tiles and/or stitched image to import"
			JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
			return false
		}
		return true			
    }
} 


/**
 * Logger methods
 */
def getErrorStackTraceAsString(Exception e){
    return Arrays.stream(e.getStackTrace()).map(StackTraceElement::toString).reduce("",(a, b)->a + "     at "+b+"\n");
}
def IJLoggerError(String message){
	IJ.log(getCurrentDateAndHour() + "   [ERROR]        "+message); 
}
def IJLoggerError(String title, String message){
	IJ.log(getCurrentDateAndHour() + "   [ERROR]        ["+title+"] -- "+message); 
}
def IJLoggerError(String title, String message, Exception e){
    IJLoggerError(title, message);
    IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e));
}
def IJLoggerError(String message, Exception e){
    IJLoggerError(message);
    IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e));
}
def IJLoggerWarn(String message){
	IJ.log(getCurrentDateAndHour() + "   [WARNING]    "+message); 
}
def IJLoggerWarn(String title, String message){
	IJ.log(getCurrentDateAndHour() + "   [WARNING]    ["+title+"] -- "+message); 
}
def IJLoggerInfo(String message){
	IJ.log(getCurrentDateAndHour() + "   [INFO]             "+message); 
}
def IJLoggerInfo(String title, String message){
	IJ.log(getCurrentDateAndHour() + "   [INFO]             ["+title+"] -- "+message); 
}
def getCurrentDateAndHour(){
    LocalDateTime localDateTime = LocalDateTime.now();
    LocalTime localTime = localDateTime.toLocalTime();
    LocalDate localDate = localDateTime.toLocalDate();
    return ""+localDate.getYear()+
            (localDate.getMonthValue() < 10 ? "0"+localDate.getMonthValue():localDate.getMonthValue()) +
            (localDate.getDayOfMonth() < 10 ? "0"+localDate.getDayOfMonth():localDate.getDayOfMonth())+"-"+
            (localTime.getHour() < 10 ? "0"+localTime.getHour():localTime.getHour())+"h"+
            (localTime.getMinute() < 10 ? "0"+localTime.getMinute():localTime.getMinute())+"m"+
            (localTime.getSecond() < 10 ? "0"+localTime.getSecond():localTime.getSecond());
}

/*
 * imports
 */
import javax.swing.JOptionPane;
import javax.swing.JFrame;
import fr.igred.omero.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import omero.gateway.model.DatasetData
import omero.gateway.model.ProjectData
import omero.gateway.model.TagAnnotationData;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JOptionPane; 
import java.nio.charset.StandardCharsets;
import ij.IJ;
import ij.Prefs;
import ij.ImagePlus;
import ij.io.FileSaver
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.nio.file.Files;
import java.nio.file.Path;

import java.awt.AWTEvent;
import java.util.stream.Collectors
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import javax.swing.*;
import java.awt.FlowLayout;
import javax.swing.BoxLayout
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.Dimension;
import java.awt.Toolkit;
import javax.swing.JOptionPane; 