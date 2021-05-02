Surveillance Camera Project (for Raspberry Pi)
==========

****************************************

**Small project with some Java code that may be used to build a simple but effective Surveillance Camera**.

****************************************

**Experimental. For my personal use only.**

# Application

The project currently contains two classes that provide a `main`-method that can be run (a `mvnw` script is provided, and you may use Maven exec, see below).

The project has two remarkable properties:

- it transfers the picture to a network storage, so removing or destroying the pi will not delete images already taken.
- it transfers the picture only if a change is detected to reduce resource consumption (network bandwidth, storage space on the NAS).

A disadvantage of the tool is that is does not allow to take images in very short succession since the comparison requires approximately 0.5 seconds on a Raspberry Pi.
It currently takes approximately 1 image per second.

## TakePictureUponChange

The class `TakePictureUponChange` runs a script (using `ProcessBuilder`) which launches a command to take a picture.
It then compares this picture with a previous one and pushes
the image to a NAS if a change is detected. In any case, the picture on the pi will be deleted afterwards.

The program requires four command-line options, in this order:

- `threshold`: a floating-point number between 0 and 1 determining when a picture is considered to be different. You can see it as a percentage value. A good value is 0.018.
- `filenamePrefix`: a prefix for the name of the image. The image will be stored under `filenamePrefix-timeStamp.jpg`.
- `targetDir`: the directory to be used to store the image. The image will be moved to this directory if it is the first one that is taken or if the image is different from the previous image by more than `threshold`.
- `imageCommand`: the shell command to be used to take the image. Use the placeholder `{filename}` for a filename under which the image is stored in the working directory of the program.

### Running this project out of the box

Assuming a NAS is mounted under `/Volumes/nas` (see below) and there is a folder `piimages`, so the
path `/Volumes/nas/piimages` exits, then the following will take a picture approximately every second and - if it
detects changes in the image, it will transfer it to the NAS.

```
./mvnw clean install exec:java -Dexec.mainClass=com.christianfries.surveillancecamera.TakePictureUponChange -Dexec.args="0.018 image /Volumes/nas/piimages \"raspistill -th none -q 10 -t 400 -awb greyworld -o {filename}\""
```

Remark: The argument `-t 400` adds a timeout of 400 milliseconds. However, launching the app adds some more milliseconds, so
in summary, it will take a picture every 700-1000 milliseconds. The Java process runs the image comparison in parallel,
but it requires approximately 600 milliseconds to process the image (on a Pi 3B). So you have to be careful when choosing the
timeout parameter smaller.

The process of transferring the image is also running in a different thread. If images cannot be transfered fast enough they
will accumulate on the Pi's local memory.

## ImagePrune

This is a standalone version of the image comparisson operating on a given folder and deleting all images in the sequence that are too similar.

The program requires two command-line options, in this order:

- `sourceDir`: the directory to be scanned for images.
- `threshold`: a floating-point number between 0 and 1 determining when a picture is considered to be different. You can see it as a percentage value. A good value is 0.018.

**WARNING:** *Be aware that the program deletes images that are considered to be duplicates!*

## Useful stuff

Below you find some stuff that allows you to build the surveillance system with a Pi such that

- The Pi ill launch the script in the background once it is powered up.
- The Pi will wait for a WLAN network.
- The Pi will mount an AFP network drive.
- The Pi will run this app.

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

Copy the script (assuming its name is `nameOfScript`) to `/etc/init.d`. Then run

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

### Full script

The following script

1. waits for the WLAN to get connected
2. mounts a NAS
3. checks out this project
4. runs `TakePictureUponChangeTakePictureUponChange`

** WARNING: If you use this script, you should use it with your own fork of this repository to be sure what code will be run and to be sure that a future version will not break your system. Note that a script that is launched via /etc/inid.d runs under the root account!**

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

## Power Supply

It is possibly to power the Raspberry Pi though an outdoor lamp (modifications YOUR OWN RISK!).

The (European) Apple Power Plug has a useful feature. You can remove the part that goes into the socket and replace it with a conventional Euro C7 Power Cable. 

![Power Plug](/img/ApplePowerPlugiPad.png)

Then you can cut the cable and connect it - ON YOUR OWN RISK - to a power source (e.g. an insulating screw joint that is part of an outdoor lamp). 

![Cable C7](/img/CableC7.png)

If the outdoor lamp has an indoor switch, the system can be switched on an off from inside.

## Parts

- Raspberry Pi 3B (approx. 40 €) 
- Raspberry Pi Camera (approx. 20 € to 30 €)
- Raspberry Pi Housing (approx. 10 €)
- USB Cable (Raspberry Pi to USB power adapter) (approx. 3 €)
- Apple 12 W USB power adapter (approx. 20 €)
- C7 Cable (USB power adapter to power source) (approx. 3 €)

The total is approx. 100 €. Of course you may still run other stuff on the Pi.

The project can also be realized with a Pi Zero, which makes it 20 € cheaper.

License
-------

The code of package "com.christianfries.clustering" is distributed under the [Apache License version
2.0][], unless otherwise explicitly stated.

 
