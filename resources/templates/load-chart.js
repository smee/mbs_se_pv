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
// find selected chart type
    var visType=$('input[name=chart-type]:checked').val();
// remove old contents
	 var chartDiv=$('#current-chart');
	 chartDiv.empty();
 
     $('#current-chart').showLoading();
     
     if(visType == 'interactive-map'){
    	 chartDiv.append($("<div id='interactive-map'/>"));
    	 var map = L.map('interactive-map').setView([51.505, -0.09], 2);
    	 L.tileLayer(baseUrl+'/tiles/'+id+'/'+selectedSeries.join('-')+'/'+interval+'/{x}/{y}/{z}', {
    	     attribution: 'done by me :)',
    	     noWrap: true,
    	     maxZoom: 10
    	 }).addTo(map);
    	 $('#current-chart').hideLoading();    	 
     }else if(visType == 'interactive-client'){
    	 	chartDiv.append($("<div id='chart_container'><div id='y_axis'/><div id='chart'/></div><div id=\'legend\'/>"));

	    	var palette = new Rickshaw.Color.Palette();
	    	
    	 	var series=[];
    	    for(idx in selectedSeries) series.push({name: selectedSeries[idx], color:palette.color()});
    	    
	    	var ajaxGraph = new Rickshaw.Graph.Ajax( {	
	    		element: document.getElementById("chart"),
	    		width: $('#chart-width').val(),
	    		height: $('#chart-height').val(),
	    		renderer: 'line',
	    		dataURL: baseUrl+'/series-of/'+id+'/'+selectedSeries.join('-')+'/'+interval+'/data.json',
	    		onData: function(d) { return d },
	    		series: series,
	    		onComplete: function(ajaxGraph){
	    			var graph = ajaxGraph.graph;
	    			var axes = new Rickshaw.Graph.Axis.Time( { graph: graph } );
	    			var y_axis = new Rickshaw.Graph.Axis.Y( {
	    				graph: graph,
	    				orientation: 'left',
	    				tickFormat: Rickshaw.Fixtures.Number.formatKMBT,
	    				element: document.getElementById('y_axis')
	    			} );
	    			var legend = new Rickshaw.Graph.Legend( {
	    				element: document.querySelector('#legend'),
	    				graph: graph
	    			} );
	    			var hover = new Rickshaw.Graph.HoverDetail( { graph: ajaxGraph.graph } ); 
	    			var shelving = new Rickshaw.Graph.Behavior.Series.Toggle( {
	    				graph: graph,
	    				legend: legend
	    			} );
	    			var highlighter = new Rickshaw.Graph.Behavior.Series.Highlight( {
	    				graph: graph,
	    				legend: legend
	    			} );
	
	    			graph.render();
	    			$('#current-chart').hideLoading();  
	    		}
	    	} );
     }else{
    	 chartDiv.append($("<img id='chart-image' src=''/>"));
    	// create link
    	 var link=baseUrl+'/series-of/'+ id+'/'+selectedSeries.join('-')+'/'+interval+'/'+visType+'.png?width='+$('#chart-width').val()+'&height='+$('#chart-height').val();
// show chart
    	 $('#chart-image').attr('src', link).load(
    			 function(){ 
    				 $('#current-chart').hideLoading();
    			 });
     }
    return false;
})("%s", "%s");

                        