from typing import Dict
from time import time
import traceback
from omero.gateway import BlitzGateway
from PyQt6.QtWidgets import QLineEdit, QLabel, QPushButton, QMainWindow, QVBoxLayout, \
    QWidget, QApplication, QHBoxLayout, QSpinBox

"""
Code copied and adapted from 
https://github.com/MuensterImagingNetwork/OMERO_N2V/blob/a4b4f6960af2ebdd9e0c1ee299b1b62d18605193/ConvenienceFunctions/inactivate_users.py
to split the deletion from listing part
"""

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

        # inactive day fields
        inactive_day_layout = QHBoxLayout()
        inactive_day_label = QLabel("Min days of inactivation")
        inactive_day_label.setStyleSheet(FONT_SIZE)
        self.inactive_days = QSpinBox()
        self.inactive_days.setStyleSheet(FONT_SIZE)
        self.inactive_days.setMinimum(0)
        self.inactive_days.setSingleStep(1)
        inactive_day_widget = QWidget()
        inactive_day_layout.addWidget(inactive_day_label)
        inactive_day_layout.addWidget(self.inactive_days)
        inactive_day_widget.setLayout(inactive_day_layout)
        widgets.append(inactive_day_widget)

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
        inactive_days = self.inactive_days.value()
        self.close()
        run_script(host, username, password, inactive_days)


def run_script(host, username, password, min_days):
    conn = BlitzGateway(username, password, host=host, port=4064, secure=True)
    conn.connect()

    if conn.isConnected():
        print(f"Connected to {host}")
        try:
            users_dict, logout_dict = find_users(conn, min_days)
            file_content = create_file(users_dict, logout_dict)
            print("______________________________________________")
            print(f"Found {len(users_dict)} users")
            print(file_content)
            print("______________________________________________")

        except Exception as e:
            print(e)
            traceback.print_exc()
        finally:
            conn.close()
            print(f"Disconnected from {host}")


def create_file(users, logouts):
    content = "user_id,username,last logout from (days)\n"
    sorted_logouts = {k: v for k, v in sorted(logouts.items(), key=lambda item: item[1])}
    for user_id in sorted_logouts.keys():
        content += f"{user_id},{users[user_id]},{sorted_logouts[user_id]}\n"

    return content


def find_users(conn: BlitzGateway, minimum_days: int = 0) -> (Dict[int, str], Dict[int, int]):
    # Determine which users' data to consider deleting.
    # copied this code from https://github.com/ome/omero-demo-cleanup/blob/main/src/omero_demo_cleanup/library.py

    users = {}

    for result in conn.getQueryService().projection(
            "SELECT id, omeName FROM Experimenter", None
    ):
        user_id = result[0].val
        user_name = result[1].val
        # check for users you DO NOT want to touch
        if user_name not in ("public", "guest", "root", "prometheus"):
            users[user_id] = user_name

    for result in conn.getQueryService().projection(
            "SELECT DISTINCT owner.id FROM Session WHERE closed IS NULL", None
    ):
        user_id = result[0].val
        if user_id in users.keys():
            print(f'Ignoring "{users[user_id]}" (#{user_id}) who is logged in.')
            del users[user_id]

    now = time()

    logouts = {}

    for result in conn.getQueryService().projection(
            "SELECT owner.id, MAX(closed) FROM Session GROUP BY owner.id", None
    ):
        user_id = result[0].val
        if user_id not in users:
            continue

        if result[1] is None:
            # never logged in
            user_logout = 0
        else:
            # note time in seconds since epoch
            user_logout = result[1].val / 1000

        days = (now - user_logout) / (60 * 60 * 24)
        if days < minimum_days:
            print(
                'Ignoring "{}" (#{}) who logged in recently.'.format(
                    users[user_id], user_id
                )
            )
            del users[user_id]

        logouts[user_id] = days

    return users, logouts


if __name__ == "__main__":
    list_argv = []
    app = QApplication(list_argv)
    window = MainWindow()
    window.show()
    app.exec()
