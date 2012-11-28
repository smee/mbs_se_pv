(function (baseUrl, selector, cbSelector, colorSelector, linkTemplate){

	ensure({js: baseUrl+"/js/chart/d3.v2.min.js", css: baseUrl+"/css/colorbrewer.css"}, function(){
		var margin = {top: 10, right: 10, bottom: 10, left: 10},
	    width = 460 - margin.right - margin.left, // width
	    height = 56 - margin.top - margin.bottom, // height
	    cellSize = 7; // cell size

	var day = function(d){ return (d.getDay()+6)%%7; },
	    week = d3.time.format("%%W"),
	    percent = d3.format(".1%%"),
	    format = d3.time.format("%%d.%%m.%%Y"),
	    revdate = d3.time.format("%%Y%%m%%d");

	//var svg = d3.select(selector).selectAll("svg");
	
	function draw(firstYear, lastYear){		
		var svgs = d3.select(selector).selectAll("svg").data(d3.range(firstYear, lastYear+1),function(d,i) {return ""+d;});		
		
		svgs.exit().remove();
		var colorscheme = cs.val();
		var svgs=svgs.enter().append("svg")
		    .attr("width", width + margin.right + margin.left)
		    .attr("height", height + margin.top + margin.bottom)
		    .attr("class", colorscheme)
		  .append("g")
		    .attr("transform", "translate(" + (margin.left + (width - cellSize * 53) / 2) + "," + (margin.top + (height - cellSize * 7) / 2) + ")");
		
		svgs.append("text")
		    .attr("transform", "translate(-6," + cellSize * 3.5 + ")rotate(-90)")
		    .attr("text-anchor", "middle")
		    .text(String);

		var rects = svgs.selectAll("rect.day")
		                .data(function(d) { 
		                	return d3.time.days(new Date(d, 0, 1), new Date(d + 1, 0, 1)); 
		                	});
		var newrect = rects.enter()
		  .append("rect")
		    .attr("class", "day")
		    .attr("width", cellSize)
		    .attr("height", cellSize)
		    .attr("x", function(d) { return week(d) * cellSize; })
		    .attr("y", function(d) { return day(d) * cellSize; })
		    .attr("date",function(d){ return revdate(d); })
		    .datum(format);

		newrect.append("title")
		    .text(function(d) { return d; });

		svgs.selectAll("path.month")
		    .data(function(d) { return d3.time.months(new Date(d, 0, 1), new Date(d + 1, 0, 1)); })
		  .enter().append("path")
		    .attr("class", "month")
		    .attr("d", monthPath);		
		
		d3.select(selector).selectAll("svg").sort();		
	}
	
	function monthPath(t0) {
	  var t1 = new Date(t0.getFullYear(), t0.getMonth() + 1, 0),
	      d0 = +day(t0), w0 = +week(t0),
	      d1 = +day(t1), w1 = +week(t1);
	  return "M" + (w0 + 1) * cellSize + "," + d0 * cellSize
	      + "H" + w0 * cellSize + "V" + 7 * cellSize
	      + "H" + w1 * cellSize + "V" + (d1 + 1) * cellSize
	      + "H" + (w1 + 1) * cellSize + "V" + 0
	      + "H" + (w0 + 1) * cellSize + "Z";
	}
	function loadAndRender(dataUrl){
		d3.csv(dataUrl, function(csv) {
		  var data = d3.nest()
		      .key(function(d) { return d.date; })
			  .rollup(function(d){ return parseInt(d[0].num); })
		      .map(csv);
		  var allDates = d3.keys(data);
		  var minDate = allDates[0];
		  var maxDate=allDates[allDates.length - 1];
		  
		  draw(format.parse(minDate).getFullYear(),format.parse(maxDate).getFullYear());
		  
		  var minmax = d3.extent(d3.values(data));
		  if(minmax[0]==minmax[1])
			  minmax[0]=0;
		  var color = d3.scale.quantize()
		    .domain(minmax)
		    .range(d3.range(9));		  
		  
		  d3.select(selector)
		    .selectAll("svg")
		    .selectAll("rect.day")//.filter(function(d) { return d in data; })
		       .attr("class", function(d) { return "day q"+color(data[d])+"-9"; })
		       .select("title")
		          .text(function(d) { 
		        	  if(data[d]) return d + ": " + percent(data[d]/minmax[1]);
		        	  else return "";});
		});
	}
	 $(document).on('click','svg', function(e){
	 var elem = e.target;
	 if(elem.nodeName='rect' && elem.hasAttribute('date')){
		 window.location=linkTemplate+'?selected-date='+elem.getAttribute('date');
	 }
	 });
	 var cb=$(cbSelector);
	 loadAndRender(cb.val());
	 cb.change(function(){
		 loadAndRender(cb.val());
	 });
	 var cs=$(colorSelector);
	 cs.change(function(){
		 d3.select(selector).selectAll("svg").attr("class",cs.val());
	 });
	});

 
})("%s", "%s", "%s", "%s", "%s")