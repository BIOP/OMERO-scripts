import requests
from PyQt6.QtWidgets import QHBoxLayout, QLabel, QLineEdit, QWidget, QVBoxLayout, QMainWindow, QPushButton, QApplication


# Update LUTs previews on the omero-server and cache them for all users
#
# Authors:
#   Tom Boissonnet - hhu Düsseldorf - Germany


FONT_SIZE = 'font-size: 14px'
DEFAULT_HOST = 'omero.epfl.ch'
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
    """
    Update LUTs previews on the omero-server and cache them for all users

    Parameters
    ----------
    host : String
        name of the OMERO server

    username : String
        username of the OMERO user

    password : String
        password of the OMERO user

    Returns
    -------

    """
    api = f"{host}/api/v0"
    session = requests.Session()

    # --- (1) Get CSRF token ---
    session.get(f"{api}/login/")
    csrf = session.cookies.get("csrftoken")

    # --- (2) Login ---
    session.post(
        f"{api}/login/",
        json={"username": username, "password": password},
        headers={"X-CSRFToken": csrf, "Referer": f"{api}/login/"}
    )

    # --- (3) Call the LUTs endpoint ---
    luts_url = f"{host}/webgateway/luts_png/?cached=false"

    response = session.get(luts_url)

    # --- (4) Logout ---
    session.post(
        f"{api}/logout/"
    )


if __name__ == "__main__":
    list_argv = []
    app = QApplication(list_argv)
    window = MainWindow()
    window.show()
    app.exec()