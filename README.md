ImagePrune
==========

****************************************

**Small project to find similar images in a directory and remove approximate duplicates or to take pictures in a loop an transferring it if a change is detected.**

****************************************

**Experimental. For my personal use only.**

# Application

## ImagePrune

A possible application of this little program is the the implementation of a surveillance system, e.g. using a Raspberry Pi.
The Pi is taking a picture in fixed time intervals, e.g. every second or faster and pushing the picture to a NAS.
A background process then runs this the ImagePrune program from time to time to remove duplicate images.

## TakePictureUponChange

Alternatively you may use the class `TakePictureUponChange` which temporarily stores the picture on the pi, compares it to a previous picture and pushes
the picture to a nas if a change is detected. In any case, the picture on the pi will be deleted afterwards.

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
./mvnw clean install exec:java -Dexec.mainClass=com.christianfries.imageprune.TakePictureUponChange -Dexec.args="0.018 image /Volumes/nas/piimages \"raspistill -th none -q 10 -t 400 -awb greyworld -o {filename}\""
```


License
-------

The code of package "com.christianfries.clustering" is distributed under the [Apache License version
2.0][], unless otherwise explicitly stated.

 
