    $.widget( "custom.tree", {
        // default options
        options: {
            jsonData: null,
            showNodeFun: null,
            duration: 750,
            width: 960,
            height: 800,
            maxTextLength: 20,
        },

        // the constructor
        _create: function() {
            this.i = 0;

            var margin = {top: 0, right: 100, bottom: 0, left: 100};

            var elementId = this.element.attr("id");
            this.svg = d3.select("#" + elementId).append("svg")
                .attr("width", this.options.width)
                .attr("height", this.options.height)
                .append("g")
                .attr("transform", "translate(" + margin.left + "," + margin.top + ")");


            var width = this.element.width() - margin.right - margin.left,
                height = this.options.height - margin.top - margin.bottom;

            this.tree = d3.layout.tree()
                .size([height, width]);

            this.diagonal = d3.svg.diagonal()
                .projection(function (d) {
                    return [d.y, d.x];
                });


            var that = this;
            that.root = this.options.jsonData;
            that.root.x0 = height / 2;
            that.root.y0 = 0;

            function collapse(d) {
                if (d.children) {
                    d._children = d.children;
                    d._children.forEach(collapse);
                    d.children = null;
                }
            }

            that.root.children.forEach(collapse);
            that._update(that.root);

            d3.select(self.frameElement).style("height", this.options.height + "px");

            $(window).resize(function(){
                that._update(that.root);
            });
        },

        _update: function(source) {
            var that = this;

            // Compute the new tree layout.
            var nodes = this.tree.nodes(this.root).reverse(),
                links = this.tree.links(nodes);

            var depths = $.map( nodes, function(val){ return val.depth; })
            var maxDepth = Math.max.apply( null, depths);

            var step = 0;
            if (maxDepth > 0)
                step = 0.75 * this.element.width() / maxDepth;

            // Normalize for fixed-depth.
            nodes.forEach(function(d) { d.y = d.depth * step; });

            // Update the nodes…
            var node = this.svg.selectAll("g.node")
                .data(nodes, function(d) {
                    return d.id || (d.id = ++(that.i));
                });

            // Enter any new nodes at the parent's previous position.
            var nodeEnter = node.enter().append("g")
                .attr("class", "node")
                .attr("transform", function(d) { return "translate(" + source.y0 + "," + source.x0 + ")"; })

            function isInnerNode(d) {
                return (d.children && d.children.length > 0) || (d._children && d._children.length > 0);
            }

            function isOpenNode(d) {
                return d._children && d._children.length > 0;
            }

            nodeEnter.append("circle")
                .attr("r", 1e-6)
                .style("fill", function(d) { return isOpenNode(d) ? "lightsteelblue" : "#fff"; })
                .on("click", function(d) {
                    if (d.children) {
                        d._children = d.children;
                        d.children = null;
                    } else {
                        d.children = d._children;
                        d._children = null;
                    }
                    that._update(d);
                })

            nodeEnter.append("text")
                .attr("x", function(d) { return isInnerNode(d) ? -10 : 10; })
                .attr("dy", ".35em")
                .attr("text-anchor", function(d) { return isInnerNode(d) ? "end" : "start"; })
                .text(function(d) {
                    var maxLength = that.options.maxTextLength;
                    return (d.name.length > maxLength) ? d.name.substring(0, maxLength - 2) + ".." : d.name;
                })
                .style("fill-opacity", 1e-6)
                .on("click", function(d) {
                    that.options.showNodeFun(d)
                })

            // Transition nodes to their new position.
            var nodeUpdate = node.transition()
                .duration(this.options.duration)
                .attr("transform", function(d) { return "translate(" + d.y + "," + d.x + ")"; });

            nodeUpdate.select("circle")
                .attr("r", 7)
                .style("fill", function(d) { return isOpenNode(d) ? "lightsteelblue" : "#fff"; });

            nodeUpdate.select("text")
                .style("fill-opacity", 1);

            // Transition exiting nodes to the parent's new position.
            var nodeExit = node.exit().transition()
                .duration(this.options.duration)
                .attr("transform", function(d) { return "translate(" + source.y + "," + source.x + ")"; })
                .remove();

            nodeExit.select("circle")
                .attr("r", 1e-6);

            nodeExit.select("text")
                .style("fill-opacity", 1e-6);

            // Update the links…
            var link = this.svg.selectAll("path.link")
                .data(links, function(d) { return d.target.id; });

            // Enter any new links at the parent's previous position.
            link.enter().insert("path", "g")
                .attr("class", "link")
                .attr("d", function(d) {
                    var o = {x: source.x0, y: source.y0};
                    return that.diagonal({source: o, target: o});
                });

            // Transition links to their new position.
            link.transition()
                .duration(this.options.duration)
                .attr("d", this.diagonal);

            // Transition exiting nodes to the parent's new position.
            link.exit().transition()
                .duration(this.options.duration)
                .attr("d", function(d) {
                    var o = {x: source.x, y: source.y};
                    return that.diagonal({source: o, target: o});
                })
                .remove();

            // Stash the old positions for transition.
            nodes.forEach(function(d) {
                d.x0 = d.x;
                d.y0 = d.y;
            });
        }
    });