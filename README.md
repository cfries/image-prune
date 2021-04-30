Surveillance Camera Project (for Raspberry Pi)
==========

****************************************

**Small project with some Java code that may be used to build a simple but effective Surveillance Camera**.

****************************************

**Experimental. For my personal use only.**

# Application

The project currently contains two classes that provide a `main`-method that can be run (a `mvnw` script is provided and you may use Maven exec, see below).

The project has two remarkable properties:

- it transfers the picture to a network storage, so removing or destroying the pi will not delete images already taken.
- it transfers the picture only, if a change is detected to reduce resource consumption (network bandwidth, storage space on the nas).

A disadvantage of the tool is that is does not allow to take images in very short succession, since the comparison requires approximately 0.5 seconds on a Raspbarry Pi.
It currently take approximately 1 image per second.

## TakePictureUponChange

The class `TakePictureUponChange` runs a script (using `ProcessBuilder`) which launches a command to take a picture.
It then compares this picture with a previous one and pushes
the picture to a nas if a change is detected. In any case, the picture on the pi will be deleted afterwards.

The program requires four command line options, in this order:

- `threshold`: a floating point number between 0 and 1 determining when a picture is considered to be different. You can see it as a percentage value. A good value is 0.018.
- `filenamePrefix`: a prefix for the name of the image. The image will be stored under `filenamePrefix-timeStamp.jpg`.
- `targetDir`: the directory to be used to store the image. The image will be moved to this directory if it is the first one that is taken or if the image is different from the previous image by more than <threshold>.
- `imageCommand`: the shell command to be used to take the image. Use the placeholder `{filename}` for a filename under which the image is stored in the working directory of the program.

## ImagePrune

A possible application of this little program is the the implementation of a surveillance system, e.g. using a Raspberry Pi.
The Pi is taking a picture in fixed time intervals, e.g. every second or faster and pushing the picture to a NAS.
A background process then runs this the ImagePrune program from time to time to remove duplicate images.


## Useful stuff

### Mounting AFP Network Drive on Raspberry Pi

#### Installing AFP Support (once)

```
sudo apt-get install afpfs-ng -y
sudo apt-get install fuse -y
sudo mkdir -p /Volumes/nameOfTheVolume
sudo chown pi:pi /Volumes/nameOfTheVolume
```

#### Mounting a Volume

```
afp_client mount -u userNameOnServer -p passwordOnServer ipAddressOfServer:nameOfTheVolume /Volumes/nameOfTheVolume
```

### Waiting for WLAN to be available

```
while [ "$(ifconfig wlan0 | grep inet | grep 192.168.)" = "" ];  do sleep 1; done
```

### Running a script upon startup of pi.

```
sudo update-rc.d nameOfScript defaults
```

### Taking Pictures in a Loop, Storing them with a Timestamp

```
#!/bin/bash

for(( ; ; ))
do
  timestamp=$(date +%s)
  raspistill -th none -q 10 -t 1000 -o /Volumes/nameOfVolume/nameOfDirectory/image-$timestamp.jpg
done
```

### Running this project out of the box

Assuming a NAS is mounted unter /Volumes/nas and there is a folder piimages /Volumes/nas/piimages
the following will take a picture appoximately every second and - if it
detects changes in the picture it will transfer it to the nas.

```
./mvnw clean install exec:java -Dexec.mainClass=com.christianfries.surveillancecamera.TakePictureUponChange -Dexec.args="0.018 image /Volumes/nas/piimages \"raspistill -th none -q 10 -t 400 -awb greyworld -o {filename}\""
```

### Full script

The following scripts

1. waits for the WLAN to get connected
2. mounts a NAS
3. checks out this project
4. runs `TakePictureUponChangeTakePictureUponChange`

```
#!/bin/bash

# wait for wlan
while [ "$(ifconfig wlan0 | grep inet | grep 192.168.)" = "" ];  do sleep 1; done

sleep 10

# mount nas
afp_client mount -u USERNAME -p PASSWIRD "SERVER:SHARE" /Volumes/nas

mkdir -p /tmp/image-recorder
cd /tmp/image-recorder

git clone https://github.com/cfries/surveillance-camera.git
cd surveillance-camera
git pull

./mvnw clean install exec:java -Dexec.mainClass=com.christianfries.surveillancecamera.TakePictureUponChange -Dexec.args="0.018 image /Volumes/nas/piimages \"raspistill -th none -q 10 -t 400 -awb greyworld -o {filename}\""
```

# Hardware

The following hardware may be useful:

## Power Plug

The (European) Apple Power Plug has a useful feature. You can remove the part that goes into the socket and replace it with a conventional Euro C7 Power Cable. 

![Power Plug](/img/ApplePowerPlugiPad.png)

Then you can cut the cable and connect it - ON YOUR OWN RISK - to a power source (e.g. an insulating screw joint that is part of an outdoor lamp). 

![Cable C7](/img/CableC7.png)

If the outdoor lamp has an indoor switch, the system can be switched on an off from inside.

License
-------

The code of package "com.christianfries.clustering" is distributed under the [Apache License version
2.0][], unless otherwise explicitly stated.

 
