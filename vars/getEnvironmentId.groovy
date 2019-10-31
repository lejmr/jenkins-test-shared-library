// This implements environment dispatcher function
// https://finmason.atlassian.net/wiki/spaces/PBP/pages/137789460/Development+Process+Product+Road+Maps

String call() {
  try{
    // Multibranch project
    if(BRANCH_NAME ==~ /PR-.*/){
      if(CHANGE_TARGET == 'master'){
        return "UAT"
      }
      if(CHANGE_TARGET == 'release'){
        return "QA"
      }
    }
    if(BRANCH_NAME == 'release'){
      return 'QA'
    }
    if(BRANCH_NAME == 'master'){
      return 'PROD'
    }
    return "DEV"
  }catch(e){
      // Simple pipeline project
      return "PROD"
  }
}
