# PeraPera
![LogoWtText2](https://github.com/user-attachments/assets/2628d686-0621-4797-b57d-4017c5c84b4b)
## Overview

PeraPera is a library manager and viewer for comics, manga, and webtoons (or any image sets organized into folders). It indexes your existing local directory structure into a searchable gallery without requiring you to move or rename your files. Once indexed, you can assign custom tags, track read progress, and apply ratings to titles within the gallery to filter and sort your collection.




Before:

<img width="579" height="331" alt="pilt" src="https://github.com/user-attachments/assets/258b7bdf-b202-46c5-aa1f-84be9b178f7f" />

After:

<img width="1919" height="1029" alt="pilt" src="https://github.com/user-attachments/assets/020fbc02-b67a-4a7a-8ad5-fe68f4a2dba9" />


<img width="1919" height="1029" alt="pilt" src="https://github.com/user-attachments/assets/3eb0f231-a1ee-4b00-86ec-c8823059335c" />


### You can then view the media in comic(left to right), manga(right to left) or webtoon(continuous scroll) mode.

<img width="1919" height="1079" alt="pilt" src="https://github.com/user-attachments/assets/5cb912fd-0778-47cd-bcae-9e099daede68" />
<img width="1919" height="1079" alt="pilt" src="https://github.com/user-attachments/assets/7202a704-fb88-4458-9e94-cc304cea325d" />




Customizable UI:
<img width="1066" height="718" alt="pilt" src="https://github.com/user-attachments/assets/330badd3-714d-47ba-9cf9-bb2934ad23b7" />

Example:
<img width="1919" height="1029" alt="pilt" src="https://github.com/user-attachments/assets/f69dc03e-5ff3-45a0-a3dc-49cbe76c27b7" />

## Usage

PeraPera only works on Windows

This requires a specific folder structure to work:

<img width="426" height="234" alt="pilt" src="https://github.com/user-attachments/assets/f15ddefe-9724-4a3c-97b7-62b2a976bceb" />

The program only scans two levels deep from your main directory.

  Level 1: Independent titles (folders with images).

  Level 2: Series folders (folders containing image folders).

Anything buried deeper than two folders away from the root will be ignored and might break something(not good).

### Step 0:
How to download
Go to releases

<img width="1911" height="841" alt="peraperating" src="https://github.com/user-attachments/assets/96f71b53-82dd-4163-94f7-c1681e3dc1d7" />

Download the PeraPera zip

<img width="1172" height="476" alt="pilt" src="https://github.com/user-attachments/assets/19d39905-40d3-4158-b344-640a6e911bb9" />

Unzip it and run with run.bat





### Step 1:
Choose your directory

<img width="1919" height="1030" alt="pilt" src="https://github.com/user-attachments/assets/ba29d30c-95f4-4983-bfe1-8f76468bb96b" />

### Step 2:
It creates IDs, loads them into library.json and generates lower resolution thumbnails for faster loading (can take time if you have alot)

<img width="1919" height="1030" alt="pilt" src="https://github.com/user-attachments/assets/1095abf6-b337-48bd-85ad-9b5a12d5ab39" />

>NOTE: For this program to work, it needs to create .per_id files in each of the folders that are considered Titles, this is the only change it does to the chosen library directory.
><img width="1307" height="759" alt="pilt" src="https://github.com/user-attachments/assets/59419ce0-4855-46b6-bc83-95dbe2c35508" />

### Step 2.5:

Disable "Chapters" if you have alot of series.

<img width="1458" height="413" alt="pilt" src="https://github.com/user-attachments/assets/8601faa6-9560-4379-a413-eb67dc2c4d66" />

### Step 2.55:

Add thumbnails to your series.

<img width="1919" height="417" alt="pilt" src="https://github.com/user-attachments/assets/76e816cb-bcdc-466e-8e8e-f5011aa00a2d" />

### Step 3: 
Done, look at the "Help" menu if you need more details.

<img width="1919" height="490" alt="pilt" src="https://github.com/user-attachments/assets/fd149572-7b45-4d41-9d5f-7e7edd0a8419" />

### Tags
You can generate your own tags and add them to titles/collections

<img width="462" height="230" alt="pilt" src="https://github.com/user-attachments/assets/b54c7d25-4696-4e04-9903-4832977c4fe9" />
<img width="1046" height="650" alt="pilt" src="https://github.com/user-attachments/assets/558b9510-4d62-4106-9446-f2f2408959af" />


You can then use the tag search for boolean logic with the tags, for instance here i added the tag "AbsolutePeak" to another manga and searched for tags absolutepeak ~ abysmaldogshit (~ is the logical OR operator here) and these 2 showed up

<img width="991" height="440" alt="pilt" src="https://github.com/user-attachments/assets/34602e74-c936-4b21-863f-fe8723d0f506" />


### 🔍 Tag Search Logic

| Logic | Syntax | Result |
| :--- | :--- | :--- |
| **AND** | `Tag1 , Tag2` | Only shows series that have **both** tags. |
| **OR** | `Tag1 ~ Tag2` | Shows series that have **either** Tag1 or Tag2. |
| **NOT** | `-Tag1` | **Excludes** any series that contains this tag. |
| **Grouping**| `(Tag1 ~ Tag2), Tag3` | Shows titles with Tag3 AND either Tag1 or tag2 |
| **series**| `series:seriesName` | only shows titles that belong to the collection/series with that name |




## NOTES

I am not a real programmer, this project is **purely** vibecoded. I had alot of manga downloaded that was really annoying to read so I thought it would be fun to try and program something for that. I chose Java for this project because I was initially 
planning to use this for learning Java but at some point the project became more interesting than that. Thats when I started purely prompt-mastering the heck out of this. That is also the reason why the src files are the way they are(bad). Although if anyone 
is brave enough to peek into those files and give me some tips on how to organize things better when vibecoding or how to fix some abhorrent bug that escaped me I would really appreciate that.
