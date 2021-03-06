# Copyright (c) Members of the EGEE Collaboration. 2004.
# See http://www.eu-egee.org/partners/ for details on the
# copyright holders.

# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
# either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


#############################################
#
#	WMProxyGrstGetProxyTest:
#
#	input parameters:
#		- p1 = endpoint URL
#		- p2 = proxy file pathname
#		- p3 = CAs files pathname
#
##############################################

top=../../../../../../../
top_src=../../../../../
package=org.glite.wms.wmproxy.getproxy
class=WMProxyGetNewProxyTest

## AXIS ===============
line=`more ../wmp-api-java-test.cfg | grep AXIS_LOC`
AXIS_LOC=${line##*=}

## API_JAVA  ==============
line=`more ../wmp-api-java-test.cfg | grep WMP_API_JAVA`
WMP_API_JAVA=${line##*=}

## SECURITY_TRUSTMANAGER ==============
line=`more ../wmp-api-java-test.cfg | grep SECURITY_TRUSTMANAGER`
SECURITY_TRUSTMANAGER=${line##*=}

## UTIL_JAVA ============ ==============
line=`more ../wmp-api-java-test.cfg | grep SECURITY_UTIL_JAVA`
SECURITY_UTIL_JAVA=${line##*=}

## DELEGATION_JAVA ============ ==============
line=`more ../wmp-api-java-test.cfg | grep SECURITY_DELEGATION_JAVA`
SECURITY_DELEGATION_JAVA=${line##*=}

## BOUNCYCASTLE ==============
line=`more ../wmp-api-java-test.cfg | grep BOUNCYCASTLE`
BOUNCYCASTLE=${line##*=}


for p in \
	${top_src} \
        ${WMP_API_JAVA} \
        ${SECURITY_TRUSTMANAGER} \
        ${SECURITY_UTIL_JAVA} \
        ${BOUNCYCASTLE} \
        ${AXIS_LOC}/*.jar
do
	if ( (! test -f $p) && (! test -d $p ) ) ; then
		echo "classpath - path not found: " + $p
		exit
	fi
 
	if ! printenv JSS_CLASSPATH | grep -q "${p}"; then
		if [ -n "${classpath}" ]; then
			classpath="${classpath}":"${p}"
		else
			classpath="${p}"
		fi
	fi
done
#input parameters
#----------------
p1=$1
p2=$2
p3=$3

# launching the test...
# ------------------------
CMD="${package}.${class} ${p1} ${p2} ${p3}"
echo "java ${CMD}"
java -classpath ${classpath} ${CMD}



