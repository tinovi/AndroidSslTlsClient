This is Android and JAVA demo application for [www.tinovi.io](www.tinovi.io) IoT cloud service.
It sends to cloud android GPS and sensor data to tinovi servers using plain SSL/TLS secure connection protocol.

######Demo application
1. [Download `ZiinodeSens.apk`](http://tinovi.io/get/ZiinodeSens.apk) and install to your phone. Run application; in field `DsId` by default should be named `ANDSENS`, then DsId(Datasource Identification) will be generated automatically(If you want you can type your DsId). This field  allows contain exact seven letters and numbers.
![tinoviSens APK](andSens.png)
2. In `PIN` field enter own imaginary PIN(letters and digits only).
3. In `tinoviSens` application press `CONNECT`; application connects to a server to generate DsId and will return `Not registered, please register datasource`. It should be so.
4. Log in to your [tinovi dashboard][http://tinovi.io/graf] or refresh browser(if allready logged) and go to Data sources. **Important: You should log in to tinovi dashboards from same local network as `tinovi gadget` is connected.**
5. In the `side menu` click on a link named Data sources.

	*NOTE: If this link is missing in the side menu it means that your current user does not have the Admin role for the current organization.*

6. Your `mobile phone datasource` should appear in datasource list. Click `Register` and in `Register data source` window change(you can leave it default to) default name and enter the same PIN which you was typed in the tinoviSens application.
7. Click `Add` button.
8. Come back to your phone and again click `CONNECT`; return should receive Status: `Connected OK`
9. And refresh browser or navigate to another menu and come back to `Data Sources` menu to check if datasource is online.

Read more from [Tinovi Dashboards](http://www.tinovi.io/documentation2) documentation.
