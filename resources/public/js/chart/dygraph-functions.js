(function (dygraphFunctions, $, undefined){
	//private
	
	// extracted from http://dygraphs.com/tests/interaction.js, see demo at http://dygraphs.com/tests/interaction.html
	function downV3(event, g, context) {
	  context.initializeMouseDown(event, g, context);
	  if (event.altKey || event.shiftKey) {
	    Dygraph.startZoom(event, g, context);
	  } else {
	    Dygraph.startPan(event, g, context);
	  }
	}

	function moveV3(event, g, context) {
	  if (context.isPanning) {
	    Dygraph.movePan(event, g, context);
	  } else if (context.isZooming) {
	    Dygraph.moveZoom(event, g, context);
	  }
	}

	function upV3(event, g, context) {
	  if (context.isPanning) {
	    Dygraph.endPan(event, g, context);
	  } else if (context.isZooming) {
	    Dygraph.endZoom(event, g, context);
	  }
	}

	// Take the offset of a mouse event on the dygraph canvas and
	// convert it to a pair of percentages from the bottom left. 
	// (Not top left, bottom is where the lower value is.)
	function offsetToPercentage(g, offsetX, offsetY) {
	  // This is calculating the pixel offset of the leftmost date.
	  var xOffset = g.toDomCoords(g.xAxisRange()[0], null)[0];
	  var yar0 = g.yAxisRange(0);

	  // This is calculating the pixel of the higest value. (Top pixel)
	  var yOffset = g.toDomCoords(null, yar0[1])[1];

	  // x y w and h are relative to the corner of the drawing area,
	  // so that the upper corner of the drawing area is (0, 0).
	  var x = offsetX - xOffset;
	  var y = offsetY - yOffset;

	  // This is computing the rightmost pixel, effectively defining the
	  // width.
	  var w = g.toDomCoords(g.xAxisRange()[1], null)[0] - xOffset;

	  // This is computing the lowest pixel, effectively defining the height.
	  var h = g.toDomCoords(null, yar0[0])[1] - yOffset;

	  // Percentage from the left.
	  var xPct = w == 0 ? 0 : (x / w);
	  // Percentage from the top.
	  var yPct = h == 0 ? 0 : (y / h);

	  // The (1-) part below changes it from "% distance down from the top"
	  // to "% distance up from the bottom".
	  return [xPct, (1-yPct)];
	}

	function dblClickV3(event, g, context) {
	  // Reducing by 20% makes it 80% the original size, which means
	  // to restore to original size it must grow by 25%

	  if (!(event.offsetX && event.offsetY)){
	    event.offsetX = event.layerX - event.target.offsetLeft;
	    event.offsetY = event.layerY - event.target.offsetTop;
	  }

	  var percentages = offsetToPercentage(g, event.offsetX, event.offsetY);
	  var xPct = percentages[0];
	  var yPct = percentages[1];

	  if (event.ctrlKey) {
	    zoom(g, -.25, xPct, yPct);
	  } else {
	    zoom(g, +.2, xPct, yPct);
	  }
	}

	function clickV3(event, g, context) {
	  Dygraph.cancelEvent(event);
	}

	function scrollV3(event, g, context) {
	  var normal = event.detail ? event.detail * -1 : event.wheelDelta / 40;
	  // For me the normalized value shows 0.075 for one click. If I took
	  // that verbatim, it would be a 7.5%.
	  var percentage = normal / 50;

	  if (!(event.offsetX && event.offsetY)){
	    event.offsetX = event.layerX - event.target.offsetLeft;
	    event.offsetY = event.layerY - event.target.offsetTop;
	  }

	  var percentages = offsetToPercentage(g, event.offsetX, event.offsetY);
	  var xPct = percentages[0];
	  var yPct = percentages[1];

	  zoom(g, percentage, xPct, yPct);
	  Dygraph.cancelEvent(event);
	}

	// Adjusts [x, y] toward each other by zoomInPercentage%
	// Split it so the left/bottom axis gets xBias/yBias of that change and
	// tight/top gets (1-xBias)/(1-yBias) of that change.
	//
	// If a bias is missing it splits it down the middle.
	function zoom(g, zoomInPercentage, xBias, yBias) {
		// TODO does not call the zoomCallback, so can't dynamically load new data 
	  xBias = xBias || 0.5;
	  yBias = yBias || 0.5;
	  function adjustAxis(axis, zoomInPercentage, bias) {
	    var delta = axis[1] - axis[0];
	    var increment = delta * zoomInPercentage;
	    var foo = [increment * bias, increment * (1-bias)];
	    return [ axis[0] + foo[0], axis[1] - foo[1] ];
	  }
	  var yAxes = g.yAxisRanges();
	  var newYAxes = [];
	  for (var i = 0; i < yAxes.length; i++) {
	    newYAxes[i] = adjustAxis(yAxes[i], zoomInPercentage, yBias);
	  }

	  g.updateOptions({
	    dateWindow: adjustAxis(g.xAxisRange(), zoomInPercentage, xBias),
	    valueRange: newYAxes[0]
	    });
	}

	function downV4(event, g, context) {
	  context.initializeMouseDown(event, g, context);
	  v4Active = true;
	  moveV4(event, g, context); // in case the mouse went down on a data point.
	}
	function restorePositioning(g) {
	  g.updateOptions({
	    dateWindow: null,
	    valueRange: null,
	    file: g['originalData_'] || g.file_
	  });
	}
	
	function renderHighlights(hl, threshold){
		return function(canvas, area, g) {
			  if(hl !== undefined){
				  // render all highlights (ranges on the domain)
				  for(var i=0;i<hl.length;i++){
					  var from = hl[i][0];
					  var to = hl[i][1];
					  var bottom_left = g.toDomCoords(from, -20);
					  var top_right = g.toDomCoords(to, +20);
					  
					  var left = bottom_left[0];
					  var right = top_right[0];
					  
					  canvas.fillStyle = "rgba(255, 255, 102, 1.0)";
					  canvas.fillRect(left, area.y, right - left, area.h);
				  }
			  }
			  if(threshold !== undefined){
				  // render horizontal threshold line
				  var y = g.toDomYCoord(threshold);
				  canvas.fillStyle = "black";
				  canvas.beginPath();
				  canvas.moveTo(area.x,y);
				  canvas.lineTo(area.w+area.x,y);
				  canvas.stroke();
				  canvas.closePath();
				  canvas.fillText("Grenzwert",area.x+5, y-10);
			  }
            }
	}

	  function initI18n(){
			// define and use german locale for all charts
			Date.ext.locales.de = {
					a: ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'],
					A: ['Sonntag', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag'],
					b: ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez'],
					B: ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'],
					c: '%%a %%d %%b %%Y %%T %%Z',
					p: ['AM', 'PM'],
					P: ['am', 'pm'],
					x: '%%d.%%m.%%y',
					X: '%%T'
				};
			Date.prototype.locale = 'de';
	  };
	  
	  function createSettings(config, response){
		  // create settings map for a new Dygraph instance
		  var d = response.data;
		  return { 
			  labels: response.labels,
			  title: response.title,
			  customBars: d.length > 0 && $.isArray(d[0][1]) && d[0][1].length == 3, // customBars if there are three values per series (min, mean, max),
			  avoidMinZero: true,
			  showRoller: false,//does not seem to work very well
			  labelsKMB :true,
			  digitsAfterDecimal: 3,
			  animatedZooms: true,
			  labelsSeparateLines: true,
			  highlightSeriesOpts: {
				  strokeWidth: 2,
				  strokeBorderWidth: 1,
				  highlightCircleSize: 5,
			  },
//			  timingName: "foo",
			  interactionModel : {
				  'mousedown' : downV3,
			      'mousemove' : moveV3,
			      'mouseup' : upV3,
			      'click' : clickV3,
			      'dblclick' : dblClickV3,
			      'mousewheel' : scrollV3
			  },
			  zoomCallback: function(minX, maxX, yRanges) {
				  foo(minX,maxX,config, yRanges);
	              },
			  underlayCallback: renderHighlights(response.highlights, response.threshold)
			};
	  }
	  var lastMinX=-1;
	  var lastMaxX=-1;
	  function foo(minX,maxX,config,yRanges){
		  // TODO does not work for every link! for example relative entropies should not load detail data, there is none!
		  if(Math.abs(minX-lastMinX)<=1 && Math.abs(maxX-lastMaxX)<=1)
			  return;
//		  console.log('min old: ',lastMinX,'new:',minX, 'diff:',lastMinX-minX);
//		  console.log('max old: ',lastMaxX,'new:',maxX, 'diff:',lastMaxX-maxX);
		  lastMinX=minX;
		  lastMaxX=maxX;
		  var chart = dygraphFunctions.charts[config.id];
		  minX=Math.floor(minX);
		  maxX=Math.floor(maxX);
		  var link = config.link.replace(/\d{8,12}-\d{8,12}/g, formatDate(new Date(minX),true)+'-'+formatDate(new Date(maxX),true));
		  // load detail data
		  $.getJSON(link, function(response){
			  // merge current data with new data
			  var newData = convertDates(response.data);
			  var oldData = chart.file_;
			  var lastLower = -1;
			  var firstHigher = -1;
			  var firstDetailDate=newData[0][0];
			  var lastDetailDate=newData[newData.length-1][0];
			  for(var i=0;i<oldData.length;i++){
				  if(oldData[i][0]<firstDetailDate)
					  lastLower=i;
				  if(firstHigher==-1 && oldData[i][0]>lastDetailDate)
					  firstHigher=i;
			  }
			  var lowerOld=(lastLower==-1)?[]:oldData.slice(0,lastLower+1);
			  var upperOld=(firstHigher==-1)?[]:oldData.slice(firstHigher);
			  
			  newData=lowerOld.concat(newData).concat(upperOld);
//			  console.log("old upper date:", oldData[oldData.length-1]);
//			  console.log("new upper date:", newData[newData.length-1]);
//			  console.log(yRanges);
			  chart.updateOptions({file:newData,
				    dateWindow: [minX, maxX],
				    valueRange: yRanges[0]
				    });
		});	
	}
	  
	  function defined(x){
		  return typeof x != "undefined" && x != null;
	  }
	  
	  function convertDates(data){
		  // convert unix timestamps into date instances
		  for(var i=0;i<data.length;i++) { 
			  data[i][0] = new Date(data[i][0]); 
		  };
		  return data;
	  }
	  
	  function renderChart(config, callback, response){
		  var id = config.id;
		  var dygraphChart = dygraphFunctions.charts[id];
		  
		  // create date instances from unix timestamps
		  var data = convertDates(response.data);
		  if(data.length==0){
			  $('#dygraph-chart').append("<div class='alert'><strong>Sorry!</strong> Für diesen Zeitraum liegen keine Daten vor.</div>");
			  if(defined(config.onError)) config.onError();
			  return;
		  }

		  // remove all old data if there is any
		  if(defined(dygraphChart)) {
			  dygraphChart.destroy(); 
			  dygraphFunctions.charts[id] = null;
			  $('#'+id+'-button').remove();
			  $('#'+id+'-no-hl-button').remove();
		  }
		  // create chart configuration
		  var chartSettings = createSettings(config, response);
		  chartSettings.width = config.params.width;
		  chartSettings.height = config.params.height;
		  if(defined(config.params.valueRange)) chartSettings.valueRange=config.params.valueRange;
		  // callback to modify settings
		  if(defined(callback)){ callback(chartSettings,response);};
			
		  var chartDiv = $("#"+id);
		  dygraphChart = new Dygraph(chartDiv[0], data, chartSettings);
		  dygraphChart.originalData_ = data;
		  //store reference to this chart
		  dygraphFunctions.charts[id] = dygraphChart;
		  //permanently highlight series by name, if given
		  var hls=config.params.highlightSeries;
		  if(defined(hls) && hls != ""){
			  dygraphChart.setSelection(false, hls, true);
		  }
		  // button to remove highlight
		  chartDiv.after($("<input type='button' id='"+id+"-no-hl-button' class='btn btn-warning' value='Highlight löschen' onclick='dygraphFunctions.charts[\""+id+"\"].clearSelection()'>"));
		  // button to reset zoom for this chart
		  chartDiv.after($("<input type='button' id='"+id+"-button' class='btn btn-info' value='Position wiederherstellen' onclick='dygraphFunctions.restorePositioning(dygraphFunctions.charts[\""+id+"\"])'>"));
		  return dygraphChart;
	  }
	  
	  function formatDate(date, withTime){
			var m1 = date.getMonth() + 1;
			var d1 = date.getDate();
			var h=date.getHours();
			var m=date.getMinutes();
			var s= '' + date.getFullYear() + (m1 < 10 ? '0':'') + m1 + (d1 < 10 ? '0' + d1 : d1);
			if(withTime){
				s+=(h<10?'0':'')+h+(m<10?'0':'')+m;
			}
			return s;
	  }
	  
	// public
	
	  dygraphFunctions.restorePositioning = restorePositioning;
	  
	  dygraphFunctions.createChart=function(config, callback){
		  initI18n();
		  // load chart data as json
		  $.getJSON(config.link, function(response){
			renderChart(config, callback, response);
		  });			
	  };
	  dygraphFunctions.formatDate = formatDate;
	  dygraphFunctions.charts = {};
	  
}(window.dygraphFunctions = window.dygraphFunctions || {}, jQuery));

