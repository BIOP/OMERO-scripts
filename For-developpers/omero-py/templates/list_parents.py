from omero.gateway import BlitzGateway
import traceback
from PyQt6.QtWidgets import QLineEdit, QLabel, QPushButton, QMainWindow, QVBoxLayout, \
    QWidget, QApplication, QHBoxLayout


FONT_SIZE = 'font-size: 14px'
DEFAULT_HOST = 'omero-server.epfl.ch'
DEFAULT_LIMIT = 200

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
        username = self.username.text()
        password = self.password.text()
        host = self.host.text()
        self.close()
        run_script(host, username, password)


def run_script(host, username, password):
    object_type = "Image"
    object_id = 303

    conn = BlitzGateway(username, password, host=host, port=4064, secure=True)
    conn.connect()

    if conn.isConnected():
        print(f"Connected to {host}")
        try:
            # search in all the user's group
            conn.SERVICE_OPTS.setOmeroGroup('-1')

            omero_object = conn.getObject(object_type, object_id)
            print_parent(omero_object, object_type)

        except Exception as e:
            print(e)
            traceback.print_exc()
        finally:
            conn.close()
            print(f"Disconnected from {host}")


def print_parent(omero_object, object_type):
    if object_type == 'Image' or \
            object_type == 'Dataset' or \
            object_type == 'Well' or \
            object_type == 'Plate':
        print(f'{object_type} : {omero_object.getName()} | {omero_object.getId()}')
        parent = omero_object.getParent()
        print_parent(parent, parent.OMERO_CLASS)
    if object_type == 'Screen' or object_type == 'Project':
        print(f'{object_type} : {omero_object.getName()} | {omero_object.getId()}')


if __name__ == "__main__":
    list_argv = []
    app = QApplication(list_argv)
    window = MainWindow()
    window.show()
    app.exec()

