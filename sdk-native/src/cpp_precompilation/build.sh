#
libsPattern="(arm)|(x86)"
ndk-build NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=./Android.mk NDK_APPLICATION_MK=./Application.mk NDK_LIBS_OUT=./libs NDK_OUT=./obj    
for D in ./libs/*; do
   if [ -d "${D}" ] && [[ "${D}" =~ $libsPattern ]]; then
       cp -r $D "../../libs"
       echo "${D} copied"   # your processing here
   fi
done

