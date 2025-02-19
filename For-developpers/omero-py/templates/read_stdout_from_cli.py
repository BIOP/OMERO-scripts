from io import StringIO
import sys


"""
Script copies and adapted from
https://stackoverflow.com/questions/16571150/how-to-capture-stdout-output-from-a-python-function-call

It answers the question asked here
https://forum.image.sc/t/get-report-of-omero-cli-duplicate-in-a-python-script/108819/4

"""


class Capturing(list):
    def __enter__(self):
        self._stdout = sys.stdout
        sys.stdout = self._stringio = StringIO()
        return self

    def __exit__(self, *args):
        self.extend(self._stringio.getvalue().splitlines())
        del self._stringio    # free up some memory
        sys.stdout = self._stdout


def run_script():
    with Capturing() as output:
        print('hello world')

    print('displays on screen')

    with Capturing(output) as output:  # note the constructor argument
        print('hello world2')

    print('done')
    print('output:', output)


if __name__ == "__main__":
    run_script()