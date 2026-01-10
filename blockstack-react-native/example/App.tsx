import React, { useState, useEffect } from 'react';
import {
  StyleSheet, ScrollView, View, Text, TouchableOpacity, Platform,
} from 'react-native';

import RNBlockstackSdk from 'react-native-blockstack';

const fname = 'my message/message.txt';

export default function App() {

  const [state, setState] = useState({
    loaded: false,
    userData: null,
    fileUrl: null,
    fileContents: null,
  });

  const getDecentralizedID = (result) => {
    if (Platform.OS === 'ios') return result['iss'];
    return result['decentralizedID'];
  };

  const createSession = async () => {

    console.log('blockstack:' + RNBlockstackSdk);

    const hasSession = await RNBlockstackSdk.hasSession();
    if (!hasSession['hasSession']) {
      const config = {
        appDomain: 'https://flamboyant-darwin-d11c17.netlify.app',
        scopes: ['store_write'],
        redirectUrl: '/redirect.html',
        callbackUrlScheme: 'blockstacksample',
      };
      const result = await RNBlockstackSdk.createSession(config);
      console.log('created ' + result['loaded']);
    } else {
      console.log('reusing session');
    }
    setState(prevState => ({ ...prevState, loaded: true }));
  };

  const signIn = async () => {
    console.log('signIn');
    try {
      const userData = {
        "username": "",
        "email": null,
        "profile": {
          "@type": "Person",
          "@context": "http://schema.org",
          "stxAddress": {}
        },
        "decentralizedID": "",
        "identityAddress": "",
        "appPrivateKey": "",
        "coreSessionToken": null,
        "authResponseToken": null,
        "hubUrl": "",
        "coreNode": null,
        "gaiaAssociationToken": ""
      };

      const result = await RNBlockstackSdk.updateUserData(userData);
      console.log(JSON.stringify(result));
      console.log('signIn successfully');

      const isUserSignedIn = await RNBlockstackSdk.isUserSignedIn();
      if (isUserSignedIn) {
        const userData = await RNBlockstackSdk.loadUserData();
        console.log('userData ' + JSON.stringify(userData));
        setState(prevState => ({
          ...prevState,
          userData: { decentralizedID: getDecentralizedID(userData) },
        }));
      }
    } catch (error) {
      // If user close the window, there will be an error:
      //   The operation couldn’t be completed.
      console.log(error)
    }
  };

  const signOut = async () => {
    console.log('signOut');
    const result = await RNBlockstackSdk.signUserOut();

    console.log(JSON.stringify(result));
    if (result['signedOut']) {
      setState(prevState => ({ ...prevState, userData: null }));
    }
  };

  const putFile = async () => {
    console.log('putFile');
    setState(prevState => ({ ...prevState, fileUrl: 'uploading...' }));

    //const fname = 'message.txt';
    const content = 'Hello React Native';
    const options = { encrypt: true };
    const result = await RNBlockstackSdk.putFile(fname, content, options);
    console.log(JSON.stringify(result));
    setState(prevState => ({ ...prevState, fileUrl: result['fileUrl'] }));
  };

  const getFile = async () => {
    console.log('getFile');
    setState(prevState => ({ ...prevState, fileContents: 'downloading...' }));

    try {
      //const fname = 'message.txt';
      const options = { decrypt: true };
      const result = await RNBlockstackSdk.getFile(fname, options);
      console.log(JSON.stringify(result));
      setState(prevState => ({ ...prevState, fileContents: result['fileContents'] }));
    } catch (e) {
      console.log(e);
      setState(prevState => ({ ...prevState, fileContents: 'No file or error' }));
    }
  };

  const deleteFile = async () => {
    console.log('deleteFile');
    setState(prevState => ({
      ...prevState,
      fileUrl: 'deleting...',
      fileContents: 'deleting...',
    }));

    //const fname = 'message.txt';
    const options = { wasSigned: false };
    const result = await RNBlockstackSdk.deleteFile(fname, options);
    console.log(JSON.stringify(result));
    setState(prevState => ({ ...prevState, fileUrl: null, fileContents: null }));
  };

  const performFiles = async () => {
    console.log('performFiles');

    //const pfData = '{"values":[{"id":"1708491132374-hjJQ-qGLN-1708491136062","type":"putFile","path":"links/1707816556114-IeqP/1708491132374-hjJQ-qGLN-1708491136062.json","content":"{\\"id\\":\\"1708491132374-hjJQ-qGLN-1708491136062\\",\\"url\\":\\"www.lyft.com\\",\\"addedDT\\":1708491132374,\\"decor\\":{\\"image\\":{\\"bg\\":{\\"type\\":\\"image\\",\\"value\\":\\"/static/media/silver-framed-eyeglasses-beside-white-click-pen-and-white-notebook.43cbd30b.jpg\\"},\\"fg\\":null},\\"favicon\\":{\\"bg\\":{\\"type\\":\\"color\\",\\"value\\":\\"bg-teal-300\\"}}},\\"extractedResult\\":{\\"url\\":\\"http://www.lyft.com\\",\\"status\\":\\"EXTRACT_OK\\",\\"title\\":\\"Lyft: A ride whenever you need one\\",\\"image\\":\\"https://images.ctfassets.net/q8mvene1wzq4/3amVLJGrSSKSYmDbFOCn9C/f7133270e145473d34a76d583294841d/04__2x.png\\",\\"extractedDT\\":1705309222422}}"}],"isSequential":false,"nItemsForNs":10}';
    //const pfData = '{"values":[{"values":[{"id":"images/1708491132374-hjJQ-vets-1708496809761.jpg","type":"putFile","path":"file://images/1708491132374-hjJQ-vets-1708496809761.jpg","content":""}],"isSequential":false,"nItemsForNs":10},{"id":"links/1707816556114-IeqP/1708491132374-hjJQ-UHxX-1708496809781.json","type":"putFile","path":"links/1707816556114-IeqP/1708491132374-hjJQ-UHxX-1708496809781.json","content":"{\\"id\\":\\"1708491132374-hjJQ-UHxX-1708496809781\\",\\"url\\":\\"www.lyft.com\\",\\"addedDT\\":1708491132374,\\"decor\\":{\\"image\\":{\\"bg\\":{\\"type\\":\\"image\\",\\"value\\":\\"/static/media/silver-framed-eyeglasses-beside-white-click-pen-and-white-notebook.43cbd30b.jpg\\"},\\"fg\\":null},\\"favicon\\":{\\"bg\\":{\\"type\\":\\"color\\",\\"value\\":\\"bg-teal-300\\"}}},\\"extractedResult\\":{\\"url\\":\\"http://www.lyft.com\\",\\"status\\":\\"EXTRACT_OK\\",\\"title\\":\\"Lyft: A ride whenever you need one\\",\\"image\\":\\"https://images.ctfassets.net/q8mvene1wzq4/3amVLJGrSSKSYmDbFOCn9C/f7133270e145473d34a76d583294841d/04__2x.png\\",\\"extractedDT\\":1705309222422},\\"custom\\":{\\"title\\":\\"Lyft --- bla bla bla\\",\\"image\\":\\"cdroot/images/1708491132374-hjJQ-vets-1708496809761.jpg\\"}}"}],"isSequential":true,"nItemsForNs":10}';
    const pfData = '{"values":[{"values":[{"values":[],"isSequential":false,"nItemsForNs":10},{"values":[{"id":"links/1707816556114-IeqP/1708491132374-hjJQ-UHxX-1708496809781.json","type":"deleteFile","path":"links/1707816556114-IeqP/1708491132374-hjJQ-UHxX-1708496809781.json","doIgnoreDoesNotExistError":true}],"isSequential":false,"nItemsForNs":10}],"isSequential":true,"nItemsForNs":10}],"isSequential":false,"nItemsForNs":10}';
    const results = await RNBlockstackSdk.performFiles(pfData, '');
    console.log(results);
  };

  const listFiles = async () => {
    console.log('listFiles');

    const result = await RNBlockstackSdk.listFiles();
    console.log(JSON.stringify(result));
  };

  const signECDSA = async () => {
    const privateKey = '';
    const content = 'Privacy Security UX';
    const result = await RNBlockstackSdk.signECDSA(privateKey, content);
    console.log(JSON.stringify(result));
  };

  useEffect(() => {
    const init = async () => {
      await createSession();

      const signedIn = await RNBlockstackSdk.isUserSignedIn();
      if (signedIn['signedIn']) {
        console.log('user is signed in');
        const userData = await RNBlockstackSdk.loadUserData();
        console.log('userData ' + JSON.stringify(userData));
        setState(prevState => ({
          ...prevState,
          userData: { decentralizedID: getDecentralizedID(userData) },
        }));
      }
    };
    init();
  }, []);

  let signInText;
  if (state.userData) signInText = 'Signed in as ' + state.userData.decentralizedID;
  else signInText = 'Not signed in';

  return (
    <View style={styles.container}>
      <Text style={styles.welcome}>Blockstack React Native Example</Text>
      <TouchableOpacity onPress={() => signIn()} disabled={!state.loaded || state.userData != null} style={styles.button}>
        <Text style={styles.buttonText}>Sign In</Text>
      </TouchableOpacity>
      <Text>{signInText}</Text>
      <TouchableOpacity onPress={() => signOut()} disabled={!state.loaded || state.userData == null} style={styles.button}>
        <Text style={styles.buttonText}>Sign out</Text>
      </TouchableOpacity>
      <Text>------------</Text>
      <TouchableOpacity onPress={() => putFile()} disabled={!state.loaded || state.userData == null} style={styles.button}>
        <Text style={styles.buttonText}>Put file</Text>
      </TouchableOpacity>
      <Text>{state.fileUrl}</Text>
      <TouchableOpacity onPress={() => getFile()} disabled={!state.loaded || state.userData == null} style={styles.button}>
        <Text style={styles.buttonText}>Get file</Text>
      </TouchableOpacity>
      <Text>{state.fileContents}</Text>
      <TouchableOpacity onPress={() => deleteFile()} disabled={!state.loaded || state.userData == null} style={styles.button}>
        <Text style={styles.buttonText}>Delete file</Text>
      </TouchableOpacity>
      <TouchableOpacity onPress={() => performFiles()} disabled={!state.loaded || state.userData == null} style={styles.button}>
        <Text style={styles.buttonText}>Perform files</Text>
      </TouchableOpacity>
      <TouchableOpacity onPress={() => listFiles()} disabled={!state.loaded || state.userData == null} style={styles.button}>
        <Text style={styles.buttonText}>List files</Text>
      </TouchableOpacity>
      <Text>------------</Text>
      <TouchableOpacity onPress={() => signECDSA()} disabled={!state.loaded} style={styles.button}>
        <Text style={styles.buttonText}>Sign ECDSA</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5FCFF'
  },
  welcome: {
    fontSize: 24,
    textAlign: 'center',
    margin: 10
  },
  button: {
    backgroundColor: '#333333',
    margin: 4,
    paddingTop: 8,
    paddingBottom: 8,
    paddingLeft: 12,
    paddingRight: 12,
  },
  buttonText: {
    fontSize: 16,
    color: 'white',
  },
});
