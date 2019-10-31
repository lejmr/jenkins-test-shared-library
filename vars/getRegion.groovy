// Function for getting region or currency identifier based on job name
// PROD pipelines are follow convetion:
//     XXX_yyy
//     XXX - Currency identifier, e.g., USD, CAD, etc.
//     yyy - job name, e.g., Daily-master

String call(Boolean rvalue=true){
  def regions = [
    USD: 'us-east-1',
    CAD: 'ca-central-1',
    EUR: 'eu-central-1',
    GBP: 'eu-west-2',
  ]
  try{
    r = JOB_BASE_NAME.split('_')[0]
  }catch(e){
    r = 'USD'
  }
  r = r.toUpperCase() in regions.keySet() ? r.toUpperCase():'USD'
  return rvalue ? regions[r]:r
}
