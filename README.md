# Compiler Project

## Group Elements and Participation

| Name                  | Student ID  | Participation |
| --------------------- | ----------- | ------------- |
| Ana Francisca Pacheco | up202307150 | 25%           |
| André Pinho           | up202307008 | 25%           |
| Dinis Silva           | up202306207 | 25%           |
| Duarte Martins        | up202304549 | 25%           |

## Self-Assessment

- We think our project deserves a 19,9 out of 20, given that we are uncertain about one test. Considering the even work distribution between all elements, this represents both the overall project self-assessment and each group member's.

## Implemented Extensions

Overall, we implemented all the extensions. However, from the OLLIR generation phase onwards, we did not place special emphasis on handling potential errors and/or edge cases for the Local Static Methods and Multidimensional Arrays extensions, since, in theory, their implementation would no longer impact our final grade, as we were already able to achieve the maximum number of points through the remaining extensions within their respective categories.

## Declaration of AI Tools Used

Please choose one of the two options  about AI tools use. In case tools where used, enumerate which ones, and the specific use.

Finally, check the box regarding responsibility for the work.

AI tools/services used in this work:

[] No AI tools were used.
[x] The following tools were used:
 - Gemini & Codex: Test generation, output debugging, edge case validation.

[x] All content has been reviewed, understood, validated, and we assume full responsibility for the work in this repository.

## Register Allocation Fix

We acknowledge that there was a bug in the Register Allocation implementation regarding the handling of the maximum number of available registers when the `-r` flag is used.

Since we were unable to fully determine the intended behavior from the specification and could not obtain clarification from the professor, we decided to keep the original implementation in the `main` branch and in the `cp3` tag.

However, we also developed a fix that we believe provides the most reasonable behavior. This solution considers the maximum number of registers available as the value passed through the `-r` flag plus the number of temporary variables introduced for branch conditions. The implementation of this fix is available in the `fix/regAlloc` branch. The corresponding merge request was intentionally left open.

# Repository Structure

The base repository has several folders, the main ones are:

- `src`: The source folder for the project, you will work here.
- `test`: Folder for your own tests.
- `test-public`: Public tests, similar to the majority of the private tests that will be used for evaluation. **Do not change the contents of this folder.** This folder will be modified by automatic updates during the semester.


The remaining folders are:

- `libs`: Libraries in JAR format, required for the project.
- `libs-jmm`: Java code that can be imported in your Java-- classes. Contains a `java` folder, with the source code, and a `compiled` folder with the same classes, in compiled format. The build system automatically compiles the files inside the `java` folder and stores them in the `compiled` folder.

