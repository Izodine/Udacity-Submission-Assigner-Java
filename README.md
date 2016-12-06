# Udacity-Submission-Assigner-Java
A simple Udacity submission assigner that allows selection of projects.

# Usage
The program requires that a `--auth-token` parameter be passed. When launching from the command line, simply launch the app using the `java -jar` command, and add `--auth-token` along with your API token. Example: `java -jar SubmissionAssigner.jar --auth-token yourtokengoeshere`.

The app will prompt for each project. Typing "Y" (case insensitive) will input the project into the submission request. Anything else or "N" will discard it. Each time the program checks for a project, it will also grab a `waits` request to show your position in the queue for each project.
Exmaple:
```
Add Your First VR App to submission request? (y/n)
n
Add [Egypt] Developing Android Apps: Popular Movies App Stage 2 to submission request? (y/n)
y
Add News App to submission request? (y/n)
y
Add Book Listing to submission request? (y/n)
y
Add Inventory App to submission request? (y/n)
y
Add Score Keeper to submission request? (y/n)
y
Add Habit Tracker to submission request? (y/n)
y
Add Tour guide app to submission request? (y/n)
```

It will then prompt for a delay. If, for example, you want to start pulling an hour from now:
```
Set delay in minutes: 60
```

If you want to start pulling instantly:
```
Set delay in minutes: 0
```

Example:
```
Checking...Request #45
No projects. Here is your position in the queue:
Quiz App                                                                   : Position = 2
Your First App                                                             : Position = 3
Musical Structure                                                          : Position = 2
Popular Movies, Stage 2                                                    : Position = 1
-=-=-=-=-=-=-=-=-=-=-=-=-=-
```

If you are assigned a project, you will receive a message:
```
You have been assigned a new project! See it here: <<Submission Url here>>
```

If you are already assigned the maximum or 2 projects, you will receive this message:
```
Waiting until assigned count < 2
```
The program will automatically check this to see when to start pulling again.

#Dependencies

The program uses GSON to generate and parse JSON data. You can find this library here: https://github.com/google/gson
