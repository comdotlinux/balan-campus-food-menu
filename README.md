# Extract Balan Deli Menu

### Steps to run (Once or Occasionally)
* Install [asdf](https://asdf-vm.com/guide/getting-started.html)
* Then run
  * `asdf plugin add jbang`
  * `asdf install`
* Export API Key for https://ocr.space/
  * `export OCR_API_KEY=the-actual-api-key`
  * To test set `MOCK_OCR=true`
* Export API Key for https://www.deepl.com/pro-api?cta=header-pro-api
  * `export DEEPL_API_KEY=the-actual-ddepl-api-key`
  * To Test set `MOCK_DEEPL=true`

### To Run
* `export OCR_API_KEY=the-actual-api-key` or `MOCK_OCR=true`
* `export DEEPL_API_KEY=the-actual-ddepl-api-key` or `MOCK_DEEPL=true`
* `jbang ExtractBalanDeliMenu`


### To edit in intellij
* `jbang edit -b --open=idea ExtractBalanDeliMenu.java`
  * Here `idea` must be somewhere on your classpath


