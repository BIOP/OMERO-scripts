
import traceback
import os
from omero.gateway import BlitzGateway
from PyQt6.QtWidgets import QLineEdit, QLabel, QFileDialog, QPushButton, QMainWindow, QVBoxLayout, \
    QWidget, QApplication, QHBoxLayout


FONT_SIZE = 'font-size: 14px'
DEFAULT_HOST = 'omero-server.epfl.ch'

class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()

        # main window settings
        self.setWindowTitle("Main window title")
        self.setMinimumSize(400, 100)
        widgets = []
        main_layout = QVBoxLayout()

        # host fields
        host_layout = QHBoxLayout()
        host_label = QLabel("Host")
        host_label.setStyleSheet(FONT_SIZE)
        self.host = QLineEdit()
        self.host.setStyleSheet(FONT_SIZE)
        self.host.setText(DEFAULT_HOST)
        host_widget = QWidget()
        host_layout.addWidget(host_label)
        host_layout.addWidget(self.host)
        host_widget.setLayout(host_layout)
        widgets.append(host_widget)


        # username fields
        username_layout = QHBoxLayout()
        username_label = QLabel("Username")
        username_label.setStyleSheet(FONT_SIZE)
        self.username = QLineEdit()
        self.username.setStyleSheet(FONT_SIZE)
        username_widget = QWidget()
        username_layout.addWidget(username_label)
        username_layout.addWidget(self.username)
        username_widget.setLayout(username_layout)
        widgets.append(username_widget)

        # password fields
        password_layout = QHBoxLayout()
        password_label = QLabel("Password")
        password_label.setStyleSheet(FONT_SIZE)
        self.password = QLineEdit()
        self.password.setStyleSheet(FONT_SIZE)
        self.password.setEchoMode(QLineEdit.EchoMode.Password)
        password_widget = QWidget()
        password_layout.addWidget(password_label)
        password_layout.addWidget(self.password)
        password_widget.setLayout(password_layout)
        widgets.append(password_widget)

        # images ids fields
        image_ids_layout = QHBoxLayout()
        image_ids_label = QLabel("Image IDs (URL)")
        image_ids_label.setStyleSheet(FONT_SIZE)
        self.image_ids = QLineEdit()
        self.image_ids.setStyleSheet(FONT_SIZE)
        image_ids_widget = QWidget()
        image_ids_layout.addWidget(image_ids_label)
        image_ids_layout.addWidget(self.image_ids)
        image_ids_widget.setLayout(image_ids_layout)
        widgets.append(image_ids_widget)

        # folder fields
        folder_layout = QHBoxLayout()
        folder_label = QLabel("Saving folder")
        folder_label.setStyleSheet(FONT_SIZE)
        self.folder = QLineEdit()
        self.folder.setStyleSheet(FONT_SIZE)
        folder_button = QPushButton(text="Choose")
        folder_button.clicked.connect(self.open_file_chooser)
        folder_button.setStyleSheet(FONT_SIZE)
        folder_widget = QWidget()
        folder_layout.addWidget(folder_label)
        folder_layout.addWidget(self.folder)
        folder_layout.addWidget(folder_button)
        folder_widget.setLayout(folder_layout)
        widgets.append(folder_widget)

        # buttons fields
        button_layout = QHBoxLayout()
        ok_button = QPushButton(text="OK")
        ok_button.setStyleSheet(FONT_SIZE)
        ok_button.clicked.connect(self.run_app)
        cancel_button = QPushButton(text="Cancel")
        cancel_button.setStyleSheet(FONT_SIZE)
        cancel_button.clicked.connect(self.close_app)
        button_widget = QWidget()
        button_layout.addWidget(ok_button)
        button_layout.addWidget(cancel_button)
        button_widget.setLayout(button_layout)
        widgets.append(button_widget)

        # building the main GUI
        for w in widgets:
            main_layout.addWidget(w)

        widget = QWidget()
        widget.setLayout(main_layout)

        # Set the central widget of the Window. Widget will expand
        # to take up all the space in the window by default.
        self.setCentralWidget(widget)

    def close_app(self):
        self.close()

    def run_app(self):
        host = self.host.text()
        username = self.username.text()
        password = self.password.text()
        ids = self.image_ids.text()
        folder = self.folder.text()
        self.close()
        run_script(host, username, password, folder, ids)

    def open_file_chooser(self):
        response = QFileDialog.getExistingDirectory(parent=self, caption="select a folder", directory=os.getcwd())
        self.folder.setText(str(response))

    def open_multiple_file_chooser(self):
        rsp_path_list, rsp_filter = QFileDialog.getOpenFileNames(parent=self, caption="select one or more files")
        path_list = ",".join(rsp_path_list)
        self.folder.setText(str(path_list))


def run_script(host, username, password, saving_folder, images_url):
    conn = BlitzGateway(username, password, host=host, port=4064, secure=True)
    conn.connect()

    if conn.isConnected():
        print(f"Connected to {host}")
        try:
            print("Hello !")
            print(f"Saving folder: {saving_folder}")
            print(f"Images URLs: {images_url}")

        except Exception as e:
            print(e)
            traceback.print_exc()
        finally:
            conn.close()
            print(f"Disconnect from {host}")


if __name__ == "__main__":
    list_argv = []
    app = QApplication(list_argv)
    window = MainWindow()
    window.show()
    app.exec()
