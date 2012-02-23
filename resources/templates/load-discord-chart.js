return (function(baseUrl, id){
// create selected date interval: yyyyMMdd-yyyyMMdd
	var start = $('#start-date').DatePickerGetDate(false);
    var end = $('#end-date').DatePickerGetDate(false);
    var m1=start.getMonth()+1;
    var m2=end.getMonth()+1;
    var d1=start.getDate();
    var d2=end.getDate();
    var startDate=''+start.getFullYear()+(m1<10?'0'+m1:m1)+(d1<10?'0'+d1:d1);
    var endDate  =''+end.getFullYear()+(m2<10?'0'+m2:m2)+(d2<10?'0'+d2:d2);
    var interval=startDate+'-'+endDate;
// create list of selected time series
    var selectedSeries = $.map($('#series-tree').dynatree('getSelectedNodes'), function(node){ return node.data.series; });
// do not fetch a chart without any selected series
    if(selectedSeries.length < 1) return false;
// create link
    var link=baseUrl+'/series-of/'+ id+'/'+selectedSeries[0]+'/'+interval+'/discord.png?width='+$('#chart-width').val()+'&height='+$('#chart-height').val();
     $('#current-chart').showLoading(); 
// show chart
    $('#chart-image').attr('src', link).load(function(){
    							$('#current-chart').hideLoading();
    							});
    return false;
})("%s", "%s");

                        