Expense Splitting Application - Demo Project for GUI Glue Code Synthesis
========================================================================
This repository contains an Android Application for an Expense Splitting Application.

The project can be opened in the Android Studio IDE, from where it can be built, run, and uploaded to an own phone (if in debug mode).

This application serves as a first example application for synthesizing GUI glue code. The synthesized code is checked into the repository to allow running the application without having to get the synthesis tool to run. However, the specification is part of this repository to allow changing the specification on the temporal logic level and to recompile the cell phone application afterwards.



Setting up for Synthesis
========================
1. First of all, the [GUISynth](https://github.com/tuc-es/guisynth) repository must be checked out to a directory and compiled according to the instructions in the corresponding README file.

2. Then, a symbolic link must be generated in the main directory of the checked out copy of this repository:
   ```
   ln -s </path/to/GUISynth-Repository> guisynth
   ```
   
Re-synthesizing the GUI controller
==================================
The specification can be found in the "app/synthspecs" directory. Running "./run.sh" from that directory triggers the orchestrator script of the GUISynth repository, which reads the main layout file of the ExpenseSplit application and the specification file "mainActivitySpec.txt" in the "app/synthspecs" directory, runs the game solver, and in case of a realizable specification modifies the source code file "app/src/main/java/de/safeml/expensesplit/MainActivity.java" with the new synthesized strategy. 

