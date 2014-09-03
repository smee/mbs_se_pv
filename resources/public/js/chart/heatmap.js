/**
 * Adapted from http://bl.ocks.org/mbostock/3074470
 */
(function(Heatmaps, $, undefined) {
	Heatmaps.createHeatmap = function(dataUrl, selector) {
		var width = 960,
		    height = 500;
		
		d3.json(dataUrl, function(heatmap) {
		  var dx = heatmap.data[0].length,
		      dy = heatmap.data.length;
		
		  // Fix the aspect ratio.
		   var ka = dy / dx, kb = height / width;
		   if (ka < kb) height = width * ka;
		   else width = height / ka;
	      
		  var maindiv = d3.select(selector);
		   
		  var x = d3.scale.linear()
		      .domain(heatmap.xRange)
		      .range([0, width]);
		
		  var y = d3.scale.linear()
		      .domain(heatmap.yRange)
		      .range([height, 0]);
		
		  var color = d3.scale.linear()
		      .domain([0,1,5,50,500, 1000, 2000])
		      .range(["#000","#0a0", "#6c0", "#ee0", "#eb4", "#eb9", "#fff"]);
		
		  var xAxis = d3.svg.axis()
		      .scale(x)
		      .orient("top")
		      .ticks(20);
		
		  var yAxis = d3.svg.axis()
		      .scale(y)
		      .orient("right");
		
		  var canvas = maindiv.append("canvas")
		      .attr("width", dx)
		      .attr("height", dy)
		      .style("width", width + "px")
		      .style("height", height + "px")
		      .style("position","absolute")
		      .call(drawImage);
		
		  var svg = maindiv.append("svg")
		      .attr("width", width)
		      .attr("height", height)
		    .style("position","relative");
		
		  svg.append("g")
		      .attr("class", "x axis")
		      .attr("transform", "translate(0," + height + ")")
		      .call(xAxis)
		      .call(removeZero);
		
		  svg.append("g")
		      .attr("class", "y axis")
		      .call(yAxis)
		      .call(removeZero);
		
		  // Compute the pixel colors; scaled by CSS.
		  function drawImage(canvas) {
		    var context = canvas.node().getContext("2d"),
		        image = context.createImageData(dx, dy);
		
		    for (var y = dy - 1, p = -1; y > 0; --y) {
		      for (var x = 0; x < dx; ++x) {
		        var c = d3.rgb(color(heatmap.data[y][x]));
		        image.data[++p] = c.r;
		        image.data[++p] = c.g;
		        image.data[++p] = c.b;
		        image.data[++p] = 255;
		      }
		    }
		
		    context.putImageData(image, 0, 0);
		  }
		
		  function removeZero(axis) {
		    axis.selectAll("g").filter(function(d) { return !d; }).remove();
		  }
		  function redraw(scale){
			  color=scale;
			  drawImage(canvas);
		  };
		  Heatmaps.redraw = redraw;
		});
	};
}(window.Heatmaps = window.Heatmaps || {}, jQuery, undefined));