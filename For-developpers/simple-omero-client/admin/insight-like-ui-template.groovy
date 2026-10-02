#@String(label="Host", value="omero-server.epfl.ch", persist=true) host
#@String(label="Username") USERNAME
#@String(label="Password", style='password' , persist=false) PASSWORD
 
/*   
 * Template code to create a UI where the user can choose the OMERO group, 
 * the OMERO user and the OMERO project, a bit like in OMERO.insight importer
 * 
 * 
 * Dependencies
 *  - Fiji update site OMERO 5.5-5.6
 *  - Fiji update site PTBIOP, with simple-omero-client
 * 
 * Author: Rémy Dornier, EPFL - PTBIOP 
 * Date: 2026.05.04
 * Version: 1.0.0
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
 */




// constants for GUI
String DEFAULT_PATH_KEY = "scriptDefaultDir2"
String PROCESS_PATH = "processPath";
String OMR_PRJ = "project";
String DST_NAMES = "datasets";
String IS_META = "isMeta";
String IS_SIZE = "isSizeDistribution";
String IS_STAT = "isStatistics";
String IS_ROI = "isROIs";
String IS_TURBIDITY = "isTurbidity";
String IS_DEL = "isDeleteAnnotations";
String DIR_SEPARATOR = ",";
String DATASET_SEPARATOR = "|";
ALL_MEMBERS = "All members"
KEY_SEPARATOR = ": "


// Connection to server
port = 4064
Client user_client = new Client()

try{
	user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())
}catch(Exception e){
	def message = "Cannot connect to "+host+". Please check your credentials"
	IJLoggerError("OMERO", message)
	JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
	return
}


if (user_client.isConnected()){
	IJLoggerInfo("OMERO", "Connected to "+host)

	def endedByUser = false;

	try{		         
		// get the default group 
		def loggedInUser = user_client.getUser();
		def loggedInUserFullName = loggedInUser.getFirstName() + " " + loggedInUser.getLastName() + KEY_SEPARATOR + loggedInUser.getId()
		def currentGroupID = user_client.getCurrentGroupId()
		def defaultGroup = user_client.getGroup(currentGroupID)
		def defaultGroupName = defaultGroup.getName() + KEY_SEPARATOR + defaultGroup.getId()

		// get all the groups
		def groupUserProjectDatasetMap = getAvailableGroupUsersMap(user_client)
		
		// get user's projects
		def projectWrapperList = user_client.getProjects(loggedInUser)
		def projectDatasetMap = groupUserProjectDatasetMap.get(defaultGroupName).get(loggedInUserFullName)

		projectWrapperList.each{
			projectDatasetMap.put(it.getName() + KEY_SEPARATOR + it.getId(), [])
		}
			
		// get default project and dataset names
		def defaultProjectNames = projectWrapperList.collect{ it.getName() + KEY_SEPARATOR + it.getId() }.sort()
		def defaultProject = projectWrapperList.isEmpty() ? "" : projectWrapperList.get(0)
		def defaultProjectName = projectWrapperList.isEmpty() ? "" : defaultProject.getName() + KEY_SEPARATOR + defaultProject.getId()
		def defaultDatasetNames = projectWrapperList.isEmpty() ? new ArrayList<>() : defaultProject.getDatasets().collect{ it.getName() + KEY_SEPARATOR + it.getId() }.sort()
		projectWrapperList.isEmpty() ?: projectDatasetMap.put(defaultProjectName, defaultDatasetNames)

		// generate the dialog box
		def dialog = new Dialog(user_client, groupUserProjectDatasetMap, defaultGroupName, loggedInUserFullName, defaultProjectName, defaultProjectNames, defaultDatasetNames)
	
		while(!dialog.getEnterPressed()){
	   		// Wait an answer from the user (Ok or Cancel)
		}
		
		//if ok
		if(dialog.getValidated()){
			def groupId = -1
			for(Map<String, String> selectedMap : dialog.getSelectedList()){
				
				// collect the user inputs
				String inputValues = selectedMap.collect{key, value -> ""+key+":"+value}.join("\n")
				IJLoggerInfo("LOCAL", "User input\n " + inputValues);
				
				File processedImageFolder = new File(selectedMap.get(PROCESS_PATH))
				boolean isMeta = selectedMap.get(IS_META).toLowerCase().equals("true") ? true: false
				boolean isSize = selectedMap.get(IS_SIZE).toLowerCase().equals("true") ? true: false
				boolean isStat = selectedMap.get(IS_STAT).toLowerCase().equals("true") ? true: false
				boolean isROI = selectedMap.get(IS_ROI).toLowerCase().equals("true") ? true: false
				boolean isTurbidity = selectedMap.get(IS_TURBIDITY).toLowerCase().equals("true") ? true: false
				boolean deletePrevious = selectedMap.get(IS_DEL).toLowerCase().equals("true") ? true: false
				String projectName = selectedMap.get(OMR_PRJ)
				String datasetNames = selectedMap.get(DST_NAMES)
				
				// getting the project
				IJLoggerInfo("OMERO", "Getting the project '" + projectName +"'");
				def projectId = Long.parseLong(projectName.split(KEY_SEPARATOR)[1])
				if(groupId < 0) groupId = checkAndSwitchGroup(user_client, "ProjectData", projectId)
				
				def projectWrapper = user_client.getProject(projectId)
				println projectWrapper
				
				for(String datasetName : datasetNames.split("\\"+DATASET_SEPARATOR)){
					IJLoggerInfo("OMERO", "Getting the dataset '" + datasetName +"'");
					def datasetId = Long.parseLong(datasetName.split(KEY_SEPARATOR)[1])
					
					def datasetWrapper = user_client.getDataset(datasetId)
					println datasetWrapper
				}
				
			}
		}
	}catch(Exception e){
		IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
	}finally{
		// disconnect
		user_client.disconnect()
		IJLoggerInfo("OMERO","Disconnected from "+host)		
	}
}else{
	message = "Not able to connect to "+host
	IJLoggerError("OMERO", message)
	JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
}

return


def checkAndSwitchGroup(user_client, dataType, dataId){
    // get the group ID and switch context to that group
    def img = user_client.getBrowseFacility().findObject(user_client.getCtx(), dataType, dataId, true);
    def groupId = img.getGroupId();

    if(groupId > 0) {
        if (user_client.getCurrentGroupId() != groupId){
        	println "Switching group from "+user_client.getGroup(user_client.getCurrentGroupId()).getName()+" to "+user_client.getGroup(groupId).getName()
            user_client.switchGroup(groupId);
        }
    }
    return groupId
}



def getAvailableGroupUsersMap(user_client) {
    def groupUserMap = new HashMap<>();
    def userMap = new HashMap<>()
	def groupMap = new HashMap<>()
	Map<String, Map<String, Map<String, List<String>>>> groupUserProjectDatasetMap = new TreeMap<>()
	
    // get all available groups for the current user according to his admin rights
    List<GroupWrapper> groups;
    ExperimenterWrapper loggedInUser;
    try {
        loggedInUser = user_client.getUser();
    }catch(Exception e){
        IJLoggerError("OMERO - Admin", "Cannot retrieve the user logged in user", e);
        return groupUserProjectDatasetMap
    }

    groups = loggedInUser.getGroups();

    // remove "system" and "user" groups
    groups.findAll(e->e.getId() != 0 && e.getId() != 1).each(group-> {
        try {
        	def groupKey = group.getName() + KEY_SEPARATOR + group.getId()

            // get all available users for the current group
            def owners = user_client.getGroup(group.getId()).getExperimenters()
            def ownersNameMap = new TreeMap<>()
			owners.each{
				def fullName = it.getFirstName() + " " + it.getLastName() + KEY_SEPARATOR + it.getId()
				//userMap.put(fullName, it)
				ownersNameMap.put(fullName, new TreeMap<>())
			}

            groupUserProjectDatasetMap.put(groupKey, ownersNameMap)
        }catch(Exception e){
            IJLoggerError("OMERO - Admin", "Cannot read the group "+group.getId(), e);
        }
    });

    return groupUserProjectDatasetMap
}



//logger
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




/**
 * 
 * Create the Dialog asking for the project and dataset
 * 
 * */
public class Dialog extends JFrame {
	
	private JComboBox<String> cmbProject, cmbGroup, cmbUser
    private JButton bnOk = new JButton("Finish");
    private JButton bnCancel = new JButton("Cancel");
    private JButton bnNext = new JButton("Next");
    private DefaultComboBoxModel<String> modelCmbProject;
    private DefaultComboBoxModel<String> modelCmbGroup;
    private DefaultComboBoxModel<String> modelCmbUser;
    private int nOptionMax = 6
    
    
	Client client;
	def userId;

	boolean enterPressed;
	boolean validated;
	
	String DEFAULT_PATH_KEY = "scriptDefaultDir2"
	String PROCESS_PATH = "processPath";
    String OMR_PRJ = "project";
    String DST_NAMES = "datasets";
    String IS_META = "isMeta";
    String IS_SIZE = "isSizeDistribution";
    String IS_STAT = "isStatistics";
    String IS_ROI = "isROIs";
    String IS_TURBIDITY = "isTurbidity";
	String IS_DEL = "isDeleteAnnotations";
    String DIR_SEPARATOR = ",";
    String DATASET_SEPARATOR = "|";
    String SELECT_ALL = "Select all"
    String NO_SELECTION = "NO DATASET SELECTED"
    String KEY_SEPARATOR = ": "
	
	File currentDir = IJ.getProperty(DEFAULT_PATH_KEY) == null ? new File("") : ((File)IJ.getProperty(DEFAULT_PATH_KEY))
	List<Map<String, String>> selectionList = new ArrayList<>()
	def user_client;
	Map<String, Map<String, Map<String, List<String>>>> groupUserProjectDatasetMap = new TreeMap<>()
	
	def isUnderProcessing = false
	
	public Dialog(user_client, groupUserProjectDatasetMap, defaultGroup, loggedInUser, defaultproject, defaultProjectNames, defaultDatasetNames){
		this.groupUserProjectDatasetMap = groupUserProjectDatasetMap
		this.user_client = user_client
		
		myDialog(defaultGroup, loggedInUser, defaultproject, defaultProjectNames, defaultDatasetNames)
	}
	
	// getters
	public boolean getEnterPressed(){return this.enterPressed}
	public boolean getValidated(){return this.validated}
	public List<Map<String, String>> getSelectedList(){return this.selectionList}
	
	// generate the dialog box
	public void myDialog(defaultGroup, loggedInUser, defaultproject, defaultProjectNames, defaultDatasetNames) {
		// set general frame
		this.setTitle("Select your import options on OMERO")
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
		modelCmbProject = new DefaultComboBoxModel<>((String[])defaultProjectNames);
        cmbProject = new JComboBox<>(modelCmbProject);
        if(!defaultProjectNames.isEmpty()){
        	cmbProject.setSelectedIndex(defaultProjectNames.indexOf(defaultproject));
        }
        
        // build group combo model 
        def groupList = new ArrayList<>(this.groupUserProjectDatasetMap.keySet()).sort()
		modelCmbGroup = new DefaultComboBoxModel<>((String[])groupList);
        cmbGroup = new JComboBox<>(modelCmbGroup);
        cmbGroup.setSelectedIndex(groupList.indexOf(defaultGroup));
		
        // build user combo model
        def userList = new ArrayList<>(this.groupUserProjectDatasetMap.get(defaultGroup).keySet()).sort()
		modelCmbUser = new DefaultComboBoxModel<>((String[])userList);
        cmbUser = new JComboBox<>(modelCmbUser);
        cmbUser.setSelectedIndex(userList.indexOf(loggedInUser));

        JButton dropDownButton = new JButton("Select");

    	// display the list of available datasets
		JPanel checkboxPanel = new JPanel()
		checkboxPanel.layout = new BoxLayout(checkboxPanel, BoxLayout.Y_AXIS)
		
	    JCheckBox selectAllBox = new JCheckBox(SELECT_ALL)
    	checkboxPanel.add(selectAllBox)

        JPopupMenu popupMenu = new JPopupMenu();
        def datasetNames = defaultProjectNames.isEmpty() ? new ArrayList<>() : this.groupUserProjectDatasetMap.get(defaultGroup).get(loggedInUser).get(defaultproject).sort()
        JCheckBox[] checkBoxes = new JCheckBox[datasetNames.size()]
        datasetNames.eachWithIndex{name, idx ->
        	checkBoxes[idx] = new JCheckBox(name)
        }
        
		JTextField tfSelectedDatasets = new JTextField();
		tfSelectedDatasets.setText(NO_SELECTION)
		tfSelectedDatasets.setColumns(15);
                                
        for (JCheckBox checkBox : checkBoxes) {
            checkboxPanel.add(checkBox);
            checkBox.addActionListener(e -> {
                List<String> selected = new ArrayList<>();
                for (JCheckBox cb : checkBoxes) {
                    if (cb.isSelected() && !cb.getText().equals(SELECT_ALL)) {
                        selected.add(cb.getText());
                    }
                }
                tfSelectedDatasets.setText(selected.join(DATASET_SEPARATOR))
            });
        }
        
	    selectAllBox.addActionListener {
	        def isSelected = selectAllBox.isSelected();
	        List<String> selected = new ArrayList<>();
	        checkboxPanel.getComponents().each { 
	        	JCheckBox cb = ((JCheckBox)it)
	        	cb.setSelected(isSelected) 
	        	if (cb.isSelected() && !cb.getText().equals(SELECT_ALL)) {
                    selected.add(cb.getText());
                }
	        }
	        tfSelectedDatasets.setText(selected.join(DATASET_SEPARATOR))
	    }
       
		def scrollPane = new JScrollPane(checkboxPanel)
		scrollPane.setPreferredSize(new Dimension(200, 250))
		popupMenu.add(scrollPane)
		
		dropDownButton.addActionListener(e -> {
            popupMenu.show(dropDownButton, 0, dropDownButton.getHeight());
        });


        JLabel labProcessFolder  = new JLabel("Processed Data dir");
        JTextField tfProcessFolder = new JTextField();
        tfProcessFolder.setColumns(15);
        tfProcessFolder.setText(currentDir.getAbsolutePath())
       

        // button to choose root folder
        JButton bProcessFolder = new JButton("Choose folder");
        bProcessFolder.addActionListener(e->{
            JFileChooser directoryChooser = new JFileChooser();
            directoryChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            directoryChooser.setCurrentDirectory(currentDir);
            directoryChooser.setDialogTitle("Choose the Processed Data folder");
            directoryChooser.showDialog(new JDialog(),"Select");

            if (directoryChooser.getSelectedFiles() != null){
                tfProcessFolder.setText(directoryChooser.getSelectedFile().getAbsolutePath());
                currentDir = directoryChooser.getSelectedFile()
                IJ.setProperty(DEFAULT_PATH_KEY, currentDir)
            }
        });
        
        // checkbox to import Acquisition metadata
        JCheckBox chkMetdata = new JCheckBox("Acquisition metadata");
        chkMetdata.setSelected(true);
        
        // checkbox to import Particle size distribution
        JCheckBox chkSize = new JCheckBox("Particle size distribution");
        chkSize.setSelected(true);
        
        // checkbox to import Particle statistics
        JCheckBox chkStats = new JCheckBox("Particle statistics");
        chkStats.setSelected(true);

        // checkbox to import Particle ROIs
        JCheckBox chkRois = new JCheckBox("Particle ROIs");
        chkRois.setSelected(true);
        
        // checkbox to import Particle ROIs
        JCheckBox chkTurbidity = new JCheckBox("Turbidity metrics");
        chkTurbidity.setSelected(true);
        
        // checkbox to import Acquisition metadata
        JCheckBox chkDeldata = new JCheckBox("Delete previous annotations");
        chkDeldata.setSelected(false);
        
        
        // build Combo Group
        JPanel boxComboGroup = new JPanel();
        JLabel groupLabel = new JLabel("Group");
        boxComboGroup.add(groupLabel);
        boxComboGroup.add(cmbGroup);
        boxComboGroup.setLayout(new FlowLayout());
        
        // build Combo User
        JPanel boxComboUser = new JPanel();
        JLabel userLabel = new JLabel("User");
        boxComboUser.add(userLabel);
        boxComboUser.add(cmbUser);
        boxComboUser.setLayout(new FlowLayout());
        
        // build Combo project
        JPanel boxComboProject = new JPanel();
        JLabel projectLabel = new JLabel("Project");
        boxComboProject.add(projectLabel);
        boxComboProject.add(cmbProject);
        boxComboProject.setLayout(new FlowLayout());
        
        // build Combo dataset
        JPanel boxComboDataset = new JPanel();
        JLabel datasetLabel = new JLabel("Dataset(s)");
        boxComboDataset.add(datasetLabel);
        boxComboDataset.add(dropDownButton);
        boxComboDataset.add(tfSelectedDatasets);
        boxComboDataset.setLayout(new FlowLayout());
        
        // build buttons
        JPanel boxButton = new JPanel();
        boxButton.add(bnNext);
        boxButton.add(bnOk);
        boxButton.add(bnCancel);
        boxButton.setLayout(new FlowLayout());
        
        // group / user box
        JPanel windowGroup = new JPanel();
        windowGroup.setLayout(new BoxLayout(windowGroup, BoxLayout.X_AXIS));
        windowGroup.add(boxComboGroup);
        windowGroup.add(boxComboUser);
        
        // Folder box
        JPanel windowProcessFolder = new JPanel();
        windowProcessFolder.setLayout(new BoxLayout(windowProcessFolder, BoxLayout.X_AXIS));
        windowProcessFolder.add(labProcessFolder);
        windowProcessFolder.add(tfProcessFolder);
        windowProcessFolder.add(bProcessFolder);
        
        // general panel
        JPanel windowNLGeneral = new JPanel();
        windowNLGeneral.setLayout(new BoxLayout(windowNLGeneral, BoxLayout.Y_AXIS));
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(windowProcessFolder);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(new JSeparator());
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(windowGroup);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(boxComboProject);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(boxComboDataset);
        windowNLGeneral.add(new JSeparator());
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(chkMetdata);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(chkSize);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(chkStats);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(chkRois);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(chkTurbidity);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(new JSeparator());
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(chkDeldata);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(new JSeparator());
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(boxButton);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        
        JPanel nicerWindow = new JPanel();
        nicerWindow.setLayout(new BoxLayout(nicerWindow, BoxLayout.X_AXIS));
        nicerWindow.add(Box.createRigidArea(new Dimension(5,0)));
        nicerWindow.add(windowNLGeneral);
        nicerWindow.add(Box.createRigidArea(new Dimension(5,0)));
        
                
        
     cmbGroup.addItemListener(
			new ItemListener(){
			    @Override
			    public void itemStateChanged(ItemEvent e) {
			    	// DESELECTED fires on every change too — ignore it
        			if (e.getStateChange() != ItemEvent.SELECTED) return
			    	
					// get the datasets corresponding to the selected project
			        def chosen_group = (String) cmbGroup.getSelectedItem()
			        def groupUsers = groupUserProjectDatasetMap.get(chosen_group).keySet()
			        
    		        // switch to the right group
			        def currentGroupId = Long.parseLong(chosen_group.split(KEY_SEPARATOR)[1])
			        user_client.switchGroup(currentGroupId)
			          
			        // update the dataset combo box
					modelCmbUser.removeAllElements();
					
					if(!groupUsers.isEmpty()){
	        			groupUsers.each { modelCmbUser.addElement(it) }
	        			cmbUser.setSelectedIndex(0);
					}else{
						modelCmbProject.removeAllElements()
					}
			    }
			}
		);   
                
        cmbUser.addItemListener(
			new ItemListener(){
			    @Override
			    public void itemStateChanged(ItemEvent e) {

			    	if (e.getStateChange() != ItemEvent.SELECTED) return
				    	
					// get the datasets corresponding to the selected project
			        def chosen_user = (String) cmbUser.getSelectedItem()
			        def chosen_group = (String) cmbGroup.getSelectedItem()
			        if (chosen_user == null || chosen_group == null) return
			        
			        def currentUser = user_client.getUser(Long.parseLong(chosen_user.split(KEY_SEPARATOR)[1]))
			        def projectNames = []
			        if(groupUserProjectDatasetMap.get(chosen_group).get(chosen_user).isEmpty()){
			        	def project_list = user_client.getProjects(currentUser)

	        			projectNames = project_list.collect{ it.getName() + KEY_SEPARATOR + it.getId()}.sort()
			        	projectNames.each{
							groupUserProjectDatasetMap.get(chosen_group).get(chosen_user).put(it, [])
						}
			        }else{
			        	projectNames = (new ArrayList<>(groupUserProjectDatasetMap.get(chosen_group).get(chosen_user).keySet())).sort()
			        }
			        
			        // update the dataset combo box
					modelCmbProject.removeAllElements();
					if(!projectNames.isEmpty()){
						projectNames.each { modelCmbProject.addElement(it) }
	        			cmbProject.setSelectedIndex(0);
					}else{
						checkboxPanel.removeAll();
						checkboxPanel.add(selectAllBox)
					}
			    }
			}
		);   
		
                
        // add listener on project combo box
        cmbProject.addItemListener(
			new ItemListener(){
			    @Override
			    public void itemStateChanged(ItemEvent e) {
			    	
			    	if (e.getStateChange() != ItemEvent.SELECTED) return
					
					// get the datasets corresponding to the selected project
					def chosen_user = (String) cmbUser.getSelectedItem()
			        def chosen_group = (String) cmbGroup.getSelectedItem()
			        def chosen_project = (String) cmbProject.getSelectedItem()
			        def dataset_names
			        if(groupUserProjectDatasetMap.get(chosen_group).get(chosen_user).get(chosen_project).isEmpty()){
			        	def projectId = Long.parseLong(chosen_project.split(KEY_SEPARATOR)[1])
			        	def project = user_client.getProject(projectId)
						def dataset_list = project.getDatasets()
						dataset_names = dataset_list.collect{ it.getName() + KEY_SEPARATOR + it.getId()}.sort()
						groupUserProjectDatasetMap.get(chosen_group).get(chosen_user).get(chosen_project).addAll(dataset_names)
			        }else{
			        	dataset_names = groupUserProjectDatasetMap.get(chosen_group).get(chosen_user).get(chosen_project)
			        }
			        
			        // update the dataset combo box
					checkboxPanel.removeAll();
					checkboxPanel.add(selectAllBox)
					
					checkBoxes = new JCheckBox[dataset_names.size()]
				    dataset_names.eachWithIndex{name, idx ->
				    	checkBoxes[idx] = new JCheckBox(name)
				    }
				    tfSelectedDatasets.setText(NO_SELECTION)

        			for (JCheckBox checkbox : checkBoxes) {
        				checkboxPanel.add(checkbox);
        				checkbox.addActionListener(a -> {
			                List<String> selected = new ArrayList<>();
			                for (JCheckBox cb : checkBoxes) {
			                    if (cb.isSelected()) {
			                        selected.add(cb.getText());
			                    }
			                }
			                tfSelectedDatasets.setText(selected.join(DATASET_SEPARATOR))
			            });
        			}
				    
			    }
			}
		);
		
		// add listener on Ok and Cancel button
		bnOk.addActionListener(
			new ActionListener(){
				@Override
    			public void actionPerformed(ActionEvent e) {
    				
    				def rootFolder = (String)tfProcessFolder.getText()
    				if(rootFolder != null && !rootFolder.isEmpty()){
						
						if(!checkInputs(checkboxPanel, tfProcessFolder))
							return
		
						Map<String, String> selection = new HashMap<>()
						selection.put(PROCESS_PATH, (String) tfProcessFolder.getText())
						selection.put(OMR_PRJ, (String) cmbProject.getSelectedItem())
						
						def datasets = []
						checkboxPanel.getComponents().each { 
							def chk = ((JCheckBox)it)
							if(chk.isSelected() && !chk.getText().equals(SELECT_ALL)){
								datasets.add(chk.getText())
							}
						}
						selection.put(DST_NAMES, String.valueOf(datasets.join(DATASET_SEPARATOR)))
						
						selection.put(IS_META, String.valueOf(chkMetdata.isSelected()))
						selection.put(IS_SIZE, String.valueOf(chkSize.isSelected()))
						selection.put(IS_STAT, String.valueOf(chkStats.isSelected()))
						selection.put(IS_ROI, String.valueOf(chkRois.isSelected()))
						selection.put(IS_TURBIDITY, String.valueOf(chkTurbidity.isSelected()))
						selection.put(IS_DEL, String.valueOf(chkDeldata.isSelected()))
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
					if(!checkInputs(checkboxPanel, tfProcessFolder))
						return
		
					Map<String, String> selection = new HashMap<>()
					selection.put(PROCESS_PATH, (String) tfProcessFolder.getText())
					selection.put(OMR_PRJ, (String) cmbProject.getSelectedItem())
					
					def datasets = []
					checkboxPanel.getComponents().each { 
						def chk = ((JCheckBox)it)
						if(chk.isSelected() && !chk.getText().equals(SELECT_ALL)){
							datasets.add(chk.getText())
						}
					}
					selection.put(DST_NAMES, String.valueOf(datasets.join(DATASET_SEPARATOR)))
					
					selection.put(IS_META, String.valueOf(chkMetdata.isSelected()))
					selection.put(IS_SIZE, String.valueOf(chkSize.isSelected()))
					selection.put(IS_STAT, String.valueOf(chkStats.isSelected()))
					selection.put(IS_ROI, String.valueOf(chkRois.isSelected()))
					selection.put(IS_TURBIDITY, String.valueOf(chkTurbidity.isSelected()))
					selection.put(IS_DEL, String.valueOf(chkDeldata.isSelected()))
					selectionList.add(selection)
					
					// reset UI
					checkboxPanel.getComponents().each { ((JCheckBox)it).setSelected(false) }				
					tfProcessFolder.setText("");
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
    

    private boolean checkInputs(checkboxPanel, tfProcessFolder){
    	if(tfProcessFolder.getText() == null || tfProcessFolder.getText().isEmpty() || !(new File(tfProcessFolder.getText())).exists()){
    		def message = "You must enter a valid folder for processed images"
			JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
			return false
    	}
    	
    	def isDatasetSelected = false
    	checkboxPanel.getComponents().each { 
			def chk = ((JCheckBox)it)
			if(chk.isSelected()){
				isDatasetSelected = true
			}
		}
		
		if(!isDatasetSelected){
			def message = "You must select at least one dataset"
			JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
			return false
		}

		return true			
    }
} 



// imports
import ij.IJ
import ij.WindowManager
import ij.Prefs
import ij.gui.Roi
import fr.igred.omero.*
import fr.igred.omero.meta.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import fr.igred.omero.roi.*
import omero.gateway.model.TagAnnotationData;
import omero.gateway.model.ProjectData
import omero.gateway.model.ROIData
import omero.gateway.model.DatasetData
import omero.gateway.model.ChannelData;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import omero.model.NamedValue
import java.awt.Color;


import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
import java.awt.Rectangle
import javax.swing.JOptionPane; 