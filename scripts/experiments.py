
import argparse
import subprocess
import json
from alive_progress import alive_bar

parser = argparse.ArgumentParser(description='LearnLib Experiments Runner')
parser.add_argument('algorithm', type=str,
                    help='Which learning algorithm to use: LSTAR / KV / TTT')
args = parser.parse_args()

repeats = 3
frameworks = ["MAT", "PAR"]
algorithm = args.algorithm
noises = ["INPUT", "OUTPUT", "MAPPING"]
noiseLevels = ["0.0", "0.1", "0.2", "0.3"]
minmaxs = []
queryLimit = "1000000"
targetsFolder = "path"
runnerPath = ""
javaPath = ""


def run(framework, noise, noiseLevel, minmax, target):
    command = [javaPath]
    # let us do all kinds of nasty reflection and illegal access.
    command.append("--add-opens=java.base/java.util=ALL-UNNAMED")
    command.append("--add-opens=java.base/java.util.reflect=ALL-UNNAMED")
    command.append("--add-opens=java.base/java.lang=ALL-UNNAMED")
    command.append("--add-opens=java.base/java.lang.reflect=ALL-UNNAMED")

    command.extend(["-jar", runnerPath])
    command.extend(["-f"], framework)
    command.extend(["-a"], algorithm)
    command.extend(["-min"], minmax[0])
    command.extend(["-max"], minmax[1])
    command.extend(["-n"], noise)
    command.extend(["-nl"], noiseLevel)
    command.extend(["-t"], target)

    results = []
    for _ in range(0, repeats):
        results.append(subprocess.run(command, stdout=subprocess.PIPE))
        bar()

    outInstance = json.loads(results[0].stdout.decode('utf-8'))
    outInstance["random"] = []
    outInstance["success"] = []
    outInstance["queryCount"] = []
    outInstance["symbolCount"] = []

    for result in results:
        instance = json.loads(result.stdout.decode('utf-8'))
        outInstance["random"].append(instance["random"])
        outInstance["success"].append(instance["success"])
        outInstance["queryCount"].append(instance["queryCount"])
        outInstance["symbolCount"].append(instance["symbolCount"])

    return outInstance


if __name__ == "__main__":
    for framework in frameworks:
        for noise in noises:
            for noiseLevel in noiseLevels:
                for minmax in minmaxs:
                    for target in targets:
                        uniqueExperiments = len(
                            frameworks) * len(noises) * len(noiseLevels) * len(minmaxs) * len(targets)
                        with alive_bar(uniqueExperiments) as bar:
                            run(framework, noise, noiseLevel,
                                minmax, target, bar)
