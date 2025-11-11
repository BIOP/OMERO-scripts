from omero.gateway import BlitzGateway
from omero.cli import cli_login
from omero.plugins.fs import FsControl
from omero.plugins.delete import DeleteControl
import traceback
from io import StringIO
import sys
from PyQt6.QtWidgets import QLineEdit, QLabel, QPushButton, QMainWindow, QVBoxLayout, \
    QWidget, QApplication, QHBoxLayout, QSpinBox, QCheckBox
from pathlib import Path
import os
from datetime import datetime


# Class to capture the output of the CLI command
class Capturing(list):
    def __enter__(self):
        self._stdout = sys.stdout
        sys.stdout = self._stringio = StringIO()
        return self

    def __exit__(self, *args):
        self.extend(self._stringio.getvalue().splitlines())
        del self._stringio    # free up some memory
        sys.stdout = self._stdout


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
        
        # Query limit fields
        query_limit_layout = QHBoxLayout()
        query_limit_label = QLabel("Limit size of the query")
        query_limit_label.setStyleSheet(FONT_SIZE)
        self.query_limits = QSpinBox()
        self.query_limits.setStyleSheet(FONT_SIZE)
        self.query_limits.setMinimum(0)
        self.query_limits.setMaximum(10000000)
        self.query_limits.setValue(DEFAULT_LIMIT)
        self.query_limits.setSingleStep(1)
        query_limit_widget = QWidget()
        query_limit_layout.addWidget(query_limit_label)
        query_limit_layout.addWidget(self.query_limits)
        query_limit_widget.setLayout(query_limit_layout)
        widgets.append(query_limit_widget)

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
        query_limit = self.query_limits.value()
        self.close()
        run_script(host, username, password, query_limit)


def run_script(host, username, password, query_limit):
    """
    Connect to OMERO, list the filesets not linked to any Image object and delete them if specified

    Parameters
    ----------
    host            String: name of the OMERO server
    username        String: username of the OMERO user
    password        String: password of the OMERO user
    query_limit     Integer: size of the filesets to retrieve from OMERO.
                            Should not be too large to not overload the server

    Returns
    -------

    """
    with cli_login("-u", username, "-w", password, "-s", host, "-p", "4064") as cli:
        # connect to OMERO server
        conn = BlitzGateway(client_obj=cli.get_client())

        if conn.isConnected():
            print(f"Connected to {host}")
            try:
                print("Getting filesets not linked to any images...")
                cli.register('fs', FsControl, '_')

                import_args = ["fs",
                               "sets",
                               "--without-images",
                               "--limit",
                               str(query_limit),
                               "--style=csv"]

                try:
                    with Capturing() as output:
                        cli.invoke(import_args, strict=True)

                    print("SUCCESS", f"Got all filesets not linked to any OMERO image object")
                except PermissionError as err:
                    print(f"Error during fs reading {err}")

                print("Saving report in the Downloads...")
                save_report(output, "Ghost filesets")
                print("SUCCESS", "Report saved !")

                # Prepare Delete command
                print("Getting all filesets...")
                import_args = ["fs",
                               "sets",
                               "--limit",
                               str(query_limit),
                               "--style=csv"
                               ]

                try:
                    # Run delete
                    with Capturing() as output2:
                        cli.invoke(import_args, strict=True)
                    print("SUCCESS", f"Got all filesets")
                except PermissionError as err:
                    print(f"Error during fs reading {err}")

                print("Saving report in the Downloads...")
                save_report(output2, "All filesets")
                print("SUCCESS", "Report saved !")

                # closing CLI session
                cli.get_client().closeSession()

                # delete variables to free some memory
                del output
                del output2
            except Exception as e:
                print(e)
                traceback.print_exc()
            finally:
                conn.close()
                print(f"Disconnected from {host}")



def save_report(report, name):
    """
    Save the output logs of the CLI in the Downloads folder,
    with the date-time of the current run.

    Parameters
    ----------
    name: String: name of the csv file
    report: List of CLI logs

    Returns
    -------

    """
    # Get the current date and time
    now = datetime.now()
    format_code = "%Y-%m-%d_%Hh%Mm%Ss"
    formatted_datetime = now.strftime(format_code)
    file_name = f"{formatted_datetime}-{name}.csv"
    
    # save the logs
    with open(os.path.join(Path.home(), "Downloads", file_name), "w") as f:
        f.write("\n".join(report))


if __name__ == "__main__":
    list_argv = []
    app = QApplication(list_argv)
    window = MainWindow()
    window.show()
    app.exec()
