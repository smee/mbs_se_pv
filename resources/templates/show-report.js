return (function(baseUrl, id) {
	var date = $('#start-date').DatePickerGetDate(false);
	var month = date.getMonth() + 1;
	var year = date.getFullYear();
	var link = baseUrl+'/report/'+id+'/' + year + '/' + month;
	window.open(link);
	return false;
})("%s","%s");
